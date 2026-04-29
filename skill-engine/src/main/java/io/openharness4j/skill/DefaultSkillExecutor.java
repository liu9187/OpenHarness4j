package io.openharness4j.skill;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.api.Usage;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.observability.AgentTracer;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.NoopToolExecutionHook;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.permission.ToolExecutionHook;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class DefaultSkillExecutor implements SkillExecutor {

    private final SkillRegistry skillRegistry;
    private final LLMAdapter llmAdapter;
    private final ToolRegistry toolRegistry;
    private final PermissionChecker permissionChecker;
    private final AgentTracer agentTracer;
    private final ContextManager contextManager;
    private final AgentRuntimeConfig runtimeConfig;
    private final ToolExecutionHook toolExecutionHook;

    public DefaultSkillExecutor(
            SkillRegistry skillRegistry,
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry
    ) {
        this(
                skillRegistry,
                llmAdapter,
                toolRegistry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );
    }

    public DefaultSkillExecutor(
            SkillRegistry skillRegistry,
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            ContextManager contextManager,
            AgentRuntimeConfig runtimeConfig
    ) {
        this(
                skillRegistry,
                llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                contextManager,
                runtimeConfig,
                new NoopToolExecutionHook()
        );
    }

    public DefaultSkillExecutor(
            SkillRegistry skillRegistry,
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            ContextManager contextManager,
            AgentRuntimeConfig runtimeConfig,
            ToolExecutionHook toolExecutionHook
    ) {
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry must not be null");
        this.llmAdapter = Objects.requireNonNull(llmAdapter, "llmAdapter must not be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.permissionChecker = permissionChecker == null ? new AllowAllPermissionChecker() : permissionChecker;
        this.agentTracer = agentTracer == null ? new DefaultAgentTracer() : agentTracer;
        this.contextManager = contextManager == null ? new DefaultContextManager() : contextManager;
        this.runtimeConfig = runtimeConfig == null ? AgentRuntimeConfig.defaults() : runtimeConfig;
        this.toolExecutionHook = toolExecutionHook == null ? new NoopToolExecutionHook() : toolExecutionHook;
    }

    @Override
    public SkillRunResponse run(SkillRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String traceId = traceId(request);
        Optional<SkillDefinition> loaded = skillRegistry.get(request.skillId(), request.version());
        if (loaded.isEmpty()) {
            return SkillRunResponse.failed(
                    request.skillId(),
                    request.version(),
                    SkillRunStatus.FAILED,
                    traceId,
                    "SKILL_NOT_FOUND",
                    "skill not found: " + request.skillId()
            );
        }

        SkillDefinition skill = loaded.get();
        String invalidInput = validateInput(skill, request.input());
        if (!invalidInput.isBlank()) {
            return SkillRunResponse.failed(
                    skill.id(),
                    skill.version(),
                    SkillRunStatus.INVALID_INPUT,
                    traceId,
                    "INVALID_INPUT",
                    invalidInput
            );
        }

        String missingTool = firstMissingTool(skill);
        if (!missingTool.isBlank()) {
            return SkillRunResponse.failed(
                    skill.id(),
                    skill.version(),
                    SkillRunStatus.FAILED,
                    traceId,
                    "SKILL_TOOL_NOT_FOUND",
                    "required tool not found: " + missingTool
            );
        }

        Map<String, Object> variables = variables(request);
        Map<String, Object> steps = new LinkedHashMap<>();
        variables.put("steps", steps);
        List<SkillStepResult> stepResults = new ArrayList<>();
        List<ToolCallRecord> toolCalls = new ArrayList<>();
        Usage usage = Usage.zero();

        for (SkillWorkflowStep step : skill.workflow()) {
            long startedAt = System.nanoTime();
            AgentResponse response;
            try {
                response = runStep(skill, step, request, variables, traceId);
            } catch (IllegalArgumentException ex) {
                SkillStepResult result = failedStep(step, traceId, elapsedMillis(startedAt), "INVALID_INPUT", safeMessage(ex), SkillRunStatus.INVALID_INPUT);
                stepResults.add(result);
                return finish(skill, stepResults, toolCalls, usage, traceId, result.status(), result.errorCode(), result.errorMessage());
            } catch (RuntimeException ex) {
                SkillStepResult result = failedStep(step, traceId, elapsedMillis(startedAt), "SKILL_STEP_FAILED", safeMessage(ex), SkillRunStatus.FAILED);
                stepResults.add(result);
                return finish(skill, stepResults, toolCalls, usage, traceId, result.status(), result.errorCode(), result.errorMessage());
            }

            SkillRunStatus stepStatus = stepStatus(response);
            String errorCode = firstErrorCode(response);
            String errorMessage = firstErrorMessage(response);
            SkillStepResult result = new SkillStepResult(
                    step.name(),
                    step.type(),
                    stepStatus,
                    response.content(),
                    response.toolCalls(),
                    response.usage(),
                    response.traceId(),
                    elapsedMillis(startedAt),
                    errorCode,
                    errorMessage
            );
            stepResults.add(result);
            toolCalls.addAll(response.toolCalls());
            usage = usage.plus(response.usage());
            recordStepValue(steps, step, result);

            if (stepStatus != SkillRunStatus.SUCCESS) {
                return finish(skill, stepResults, toolCalls, usage, traceId, stepStatus, errorCode, errorMessage);
            }
        }

        String output = stepResults.isEmpty() ? "" : stepResults.get(stepResults.size() - 1).output();
        return new SkillRunResponse(
                skill.id(),
                skill.version(),
                SkillRunStatus.SUCCESS,
                output,
                stepResults,
                toolCalls,
                usage,
                traceId,
                "",
                ""
        );
    }

    private AgentResponse runStep(
            SkillDefinition skill,
            SkillWorkflowStep step,
            SkillRunRequest request,
            Map<String, Object> variables,
            String traceId
    ) {
        Map<String, Object> metadata = metadata(skill, step, request, traceId);
        String input = switch (step.type()) {
            case LLM -> llmInput(skill, step, variables);
            case TOOL -> "Run skill tool step " + step.name() + " for skill " + skill.id() + ".";
        };
        DefaultAgentRuntime runtime = new DefaultAgentRuntime(
                step.type() == SkillStepType.TOOL ? fixedToolAdapter(step, variables) : llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                contextManager,
                runtimeConfigFor(step),
                toolExecutionHook
        );
        return runtime.run(new AgentRequest(request.sessionId(), request.userId(), nonBlankInput(input, step), metadata));
    }

    private LLMAdapter fixedToolAdapter(SkillWorkflowStep step, Map<String, Object> variables) {
        Map<String, Object> args = TemplateRenderer.renderMap(step.args(), variables);
        return new FixedToolStepLLMAdapter(step.name(), step.tool(), args);
    }

    private AgentRuntimeConfig runtimeConfigFor(SkillWorkflowStep step) {
        if (step.type() == SkillStepType.TOOL && runtimeConfig.maxIterations() < 2) {
            return runtimeConfig.withMaxIterations(2);
        }
        return runtimeConfig;
    }

    private String llmInput(SkillDefinition skill, SkillWorkflowStep step, Map<String, Object> variables) {
        String system = TemplateRenderer.renderString(skill.prompt().system(), variables);
        String promptTemplate = step.prompt().isBlank() ? skill.prompt().user() : step.prompt();
        String user = TemplateRenderer.renderString(promptTemplate, variables);
        if (system.isBlank()) {
            return user;
        }
        if (user.isBlank()) {
            return system;
        }
        return system + "\n\n" + user;
    }

    private static Map<String, Object> variables(SkillRunRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>(request.input());
        variables.put("input", request.input());
        variables.put("metadata", request.metadata());
        return variables;
    }

    private static Map<String, Object> metadata(
            SkillDefinition skill,
            SkillWorkflowStep step,
            SkillRunRequest request,
            String traceId
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put(DefaultAgentTracer.TRACE_ID_METADATA_KEY, traceId);
        metadata.put("skillId", skill.id());
        metadata.put("skillVersion", skill.version());
        metadata.put("skillStep", step.name());
        return metadata;
    }

    private static void recordStepValue(Map<String, Object> steps, SkillWorkflowStep step, SkillStepResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", step.name());
        value.put("type", step.type().name().toLowerCase());
        value.put("status", result.status().name());
        value.put("output", result.output());
        value.put("traceId", result.traceId());
        steps.put(step.name(), value);
    }

    @SuppressWarnings("unchecked")
    private static String validateInput(SkillDefinition skill, Map<String, Object> input) {
        Object required = skill.inputSchema().get("required");
        if (!(required instanceof Iterable<?> requiredFields)) {
            return "";
        }
        for (Object field : requiredFields) {
            String name = String.valueOf(field);
            if (!input.containsKey(name) || input.get(name) == null) {
                return "missing required input: " + name;
            }
        }
        return "";
    }

    private String firstMissingTool(SkillDefinition skill) {
        Set<String> names = new LinkedHashSet<>(skill.requiredTools());
        skill.workflow()
                .stream()
                .filter(step -> step.type() == SkillStepType.TOOL)
                .map(SkillWorkflowStep::tool)
                .forEach(names::add);
        for (String name : names) {
            if (toolRegistry.get(name).isEmpty()) {
                return name;
            }
        }
        return "";
    }

    private static SkillRunStatus stepStatus(AgentResponse response) {
        if (response.toolCalls().stream().anyMatch(call -> call.status() == ToolResultStatus.PERMISSION_DENIED)) {
            return SkillRunStatus.PERMISSION_DENIED;
        }
        if (response.toolCalls().stream().anyMatch(call -> call.status() == ToolResultStatus.FAILED)) {
            return SkillRunStatus.FAILED;
        }
        if (response.finishReason() == FinishReason.ERROR || response.finishReason() == FinishReason.MAX_ITERATION_EXCEEDED) {
            return SkillRunStatus.FAILED;
        }
        return SkillRunStatus.SUCCESS;
    }

    private static String firstErrorCode(AgentResponse response) {
        return response.toolCalls()
                .stream()
                .filter(call -> !call.errorCode().isBlank())
                .map(ToolCallRecord::errorCode)
                .findFirst()
                .orElse("");
    }

    private static String firstErrorMessage(AgentResponse response) {
        return response.toolCalls()
                .stream()
                .filter(call -> !call.errorMessage().isBlank())
                .map(ToolCallRecord::errorMessage)
                .findFirst()
                .orElse("");
    }

    private static SkillStepResult failedStep(
            SkillWorkflowStep step,
            String traceId,
            long durationMillis,
            String errorCode,
            String errorMessage,
            SkillRunStatus status
    ) {
        return new SkillStepResult(
                step.name(),
                step.type(),
                status,
                "",
                List.of(),
                Usage.zero(),
                traceId,
                durationMillis,
                errorCode,
                errorMessage
        );
    }

    private static SkillRunResponse finish(
            SkillDefinition skill,
            List<SkillStepResult> steps,
            List<ToolCallRecord> toolCalls,
            Usage usage,
            String traceId,
            SkillRunStatus status,
            String errorCode,
            String errorMessage
    ) {
        String output = steps.isEmpty() ? "" : steps.get(steps.size() - 1).output();
        return new SkillRunResponse(
                skill.id(),
                skill.version(),
                status,
                output,
                steps,
                toolCalls,
                usage,
                traceId,
                errorCode,
                errorMessage
        );
    }

    private static String traceId(SkillRunRequest request) {
        Object provided = request.metadata().get(DefaultAgentTracer.TRACE_ID_METADATA_KEY);
        if (provided instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }

    private static String nonBlankInput(String input, SkillWorkflowStep step) {
        if (input == null || input.isBlank()) {
            return "Run skill step " + step.name() + ".";
        }
        return input;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static final class FixedToolStepLLMAdapter implements LLMAdapter {
        private final String stepName;
        private final String toolName;
        private final Map<String, Object> args;
        private boolean emitted;

        private FixedToolStepLLMAdapter(String stepName, String toolName, Map<String, Object> args) {
            this.stepName = stepName;
            this.toolName = toolName;
            this.args = args;
        }

        @Override
        public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools) {
            if (!emitted) {
                emitted = true;
                return LLMResponse.toolCalls(
                        "calling " + toolName,
                        List.of(new ToolCall("skill_" + stepName, toolName, args))
                );
            }
            String output = messages.stream()
                    .filter(message -> message.role() == MessageRole.TOOL)
                    .reduce((first, second) -> second)
                    .map(Message::content)
                    .orElse("");
            return LLMResponse.text(output);
        }
    }
}

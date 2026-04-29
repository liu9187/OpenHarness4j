package io.openharness4j.cli;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.Usage;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.LLMProviderProfile;
import io.openharness4j.llm.LLMProviderProfileFactory;
import io.openharness4j.llm.MockLLMAdapter;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.permission.PermissionPolicy;
import io.openharness4j.permission.PolicyPermissionChecker;
import io.openharness4j.permission.ToolPermissionRule;
import io.openharness4j.runtime.AgentEvent;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.skill.DefaultSkillExecutor;
import io.openharness4j.skill.InMemorySkillRegistry;
import io.openharness4j.skill.SkillDefinition;
import io.openharness4j.skill.SkillRunRequest;
import io.openharness4j.skill.SkillRunResponse;
import io.openharness4j.tool.InMemoryToolRegistry;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class OpenHarnessCli {

    public static void main(String[] args) {
        int exitCode = new OpenHarnessCli().run(args, System.in, System.out, System.err);
        System.exit(exitCode);
    }

    public int run(String[] args, InputStream input, PrintStream out, PrintStream err) {
        try {
            CliOptions options = CliOptionParser.parse(args);
            if (options.help) {
                out.print(help());
                return 0;
            }
            if (options.dryRun) {
                return dryRun(options, out);
            }
            if (!options.hasPrompt() && !options.interactive) {
                options.interactive = true;
            }
            LLMAdapter llmAdapter = llmAdapter(options);
            InMemoryToolRegistry toolRegistry = CliToolFactory.create(options);
            PermissionChecker permissionChecker = permissionChecker(options);
            if (options.interactive && !options.hasPrompt()) {
                return interactive(options, llmAdapter, toolRegistry, permissionChecker, input, out);
            }
            return runPrompt(options, llmAdapter, toolRegistry, permissionChecker, options.prompt, out);
        } catch (IllegalArgumentException ex) {
            err.println("error: " + safeMessage(ex));
            return 2;
        } catch (RuntimeException ex) {
            err.println("error: " + safeMessage(ex));
            return 1;
        }
    }

    private int dryRun(CliOptions options, PrintStream out) {
        CliDryRunReport report = CliDryRun.evaluate(options);
        if (options.outputFormat == OutputFormat.JSON || options.outputFormat == OutputFormat.STREAM_JSON) {
            out.println(CliJson.write(report));
        } else {
            out.print(CliDryRun.formatPlain(report));
        }
        return report.ready() ? 0 : 2;
    }

    private int interactive(
            CliOptions options,
            LLMAdapter llmAdapter,
            InMemoryToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            InputStream input,
            PrintStream out
    ) {
        Scanner scanner = new Scanner(input, StandardCharsets.UTF_8);
        while (true) {
            out.print("oh> ");
            out.flush();
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                break;
            }
            if (!line.isBlank()) {
                runPrompt(options, llmAdapter, toolRegistry, permissionChecker, line, out);
            }
        }
        return 0;
    }

    private int runPrompt(
            CliOptions options,
            LLMAdapter llmAdapter,
            InMemoryToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            String prompt,
            PrintStream out
    ) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (options.skillId != null && !options.skillId.isBlank()) {
            SkillRunResponse response = runSkill(options, llmAdapter, toolRegistry, permissionChecker, prompt);
            writeSkillResponse(options.outputFormat, response, out);
            return response.status().name().equals("SUCCESS") ? 0 : 1;
        }

        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                toolRegistry,
                permissionChecker,
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );
        AgentResponse response = runtime.run(
                AgentRequest.of(options.sessionId, options.userId, prompt),
                event -> {
                    if (options.outputFormat == OutputFormat.STREAM_JSON) {
                        out.println(CliJson.write(eventMap(event)));
                    }
                }
        );
        writeAgentResponse(options.outputFormat, response, out);
        return 0;
    }

    private SkillRunResponse runSkill(
            CliOptions options,
            LLMAdapter llmAdapter,
            InMemoryToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            String prompt
    ) {
        InMemorySkillRegistry skillRegistry = CliSkillLoader.load(options);
        SkillDefinition skill = skillRegistry.get(options.skillId)
                .orElseThrow(() -> new IllegalArgumentException("unknown skill: " + options.skillId));
        DefaultSkillExecutor executor = new DefaultSkillExecutor(
                skillRegistry,
                llmAdapter,
                toolRegistry,
                permissionChecker,
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("input", prompt);
        input.put("prompt", prompt);
        input.put("text", prompt);
        input.put("topic", prompt);
        return executor.run(SkillRunRequest.of(skill.id(), options.sessionId, options.userId, input));
    }

    private static LLMAdapter llmAdapter(CliOptions options) {
        if (options.hasMockProvider()) {
            return new MockLLMAdapter(List.of(), (messages, tools) -> LLMResponse.text(options.mockResponse));
        }
        if (!options.hasConfiguredProvider()) {
            throw new IllegalArgumentException("missing provider; use --mock-response or configure --provider-endpoint and --provider-model");
        }
        LLMProviderProfile profile = new LLMProviderProfile(
                options.providerName,
                options.providerEndpoint,
                options.providerApiKey,
                options.providerApiKeyEnv,
                options.providerModel,
                options.providerModelEnv,
                true
        );
        return new LLMProviderProfileFactory().adapter(profile);
    }

    private static PermissionChecker permissionChecker(CliOptions options) {
        if (options.deniedTools.isEmpty()) {
            return new AllowAllPermissionChecker();
        }
        List<ToolPermissionRule> rules = new ArrayList<>();
        options.deniedTools.stream()
                .map(CliToolFactory::normalize)
                .forEach(tool -> rules.add(ToolPermissionRule.deny(
                        tool,
                        RiskLevel.HIGH,
                        "tool denied by CLI policy: " + tool
                )));
        return new PolicyPermissionChecker(new PermissionPolicy(true, rules));
    }

    private static void writeAgentResponse(OutputFormat outputFormat, AgentResponse response, PrintStream out) {
        if (outputFormat == OutputFormat.PLAIN) {
            out.println(response.content());
            return;
        }
        Map<String, Object> payload = responseMap(response);
        if (outputFormat == OutputFormat.STREAM_JSON) {
            payload = new LinkedHashMap<>(payload);
            payload.put("type", "FINAL_RESPONSE");
        }
        out.println(CliJson.write(payload));
    }

    private static void writeSkillResponse(OutputFormat outputFormat, SkillRunResponse response, PrintStream out) {
        if (outputFormat == OutputFormat.PLAIN) {
            out.println(response.output());
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", outputFormat == OutputFormat.STREAM_JSON ? "SKILL_RESPONSE" : "response");
        payload.put("skillId", response.skillId());
        payload.put("status", response.status().name());
        payload.put("output", response.output());
        payload.put("traceId", response.traceId());
        payload.put("toolCalls", response.toolCalls().size());
        out.println(CliJson.write(payload));
    }

    private static Map<String, Object> responseMap(AgentResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", response.content());
        payload.put("finishReason", response.finishReason().name());
        payload.put("traceId", response.traceId());
        payload.put("usage", usageMap(response.usage()));
        payload.put("toolCalls", response.toolCalls());
        return payload;
    }

    private static Map<String, Object> eventMap(AgentEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.type().name());
        payload.put("traceId", event.traceId());
        payload.put("timestamp", event.timestamp().toString());
        payload.put("message", event.message());
        payload.put("toolCallId", event.toolCallId());
        payload.put("toolName", event.toolName());
        payload.put("usage", usageMap(event.usage()));
        payload.put("cost", Map.of("currency", event.cost().currency(), "amount", event.cost().amount()));
        payload.put("attempt", event.attempt());
        payload.put("metadata", event.metadata());
        return payload;
    }

    private static Map<String, Object> usageMap(Usage usage) {
        return Map.of(
                "promptTokens", usage.promptTokens(),
                "completionTokens", usage.completionTokens(),
                "totalTokens", usage.totalTokens()
        );
    }

    private static String help() {
        return """
                OpenHarness4j CLI

                Usage:
                  oh -p "prompt" [--mock-response text] [--output plain|json|stream-json]
                  oh --interactive [--mock-response text]
                  oh --dry-run [--output plain|json]

                Runtime options:
                  -p, --prompt TEXT              Run one prompt.
                  -i, --interactive              Read prompts from stdin until exit or quit.
                  --session ID                   Session id. Default: cli-session.
                  --user ID                      User id. Default: cli-user.
                  --output FORMAT                plain, json, or stream-json.
                  --mock-response TEXT           Use a local mock LLM response.
                  --provider-endpoint URL        OpenAI-compatible chat completions endpoint.
                  --provider-model MODEL         Provider model.
                  --provider-api-key-env NAME    Environment variable for API key.
                  --enable-tool NAME[,NAME]      Enable built-in echo,file,shell,web_fetch,search,mcp_call.
                  --deny-tool NAME[,NAME]        Deny tool names through permission policy.
                  --tool NAME[,NAME]             Declare intended tool use for dry-run checks.
                  --skill ID                     Select a skill for dry-run or execution.
                  --skill-location PATH          Load .md/.yaml/.yml skill file or directory.
                  --mcp-server NAME              Declare MCP server readiness for dry-run.
                  --dry-run                      Preview readiness without model/tool/sub-agent execution.
                  -h, --help                     Show this help.
                """;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}

package io.openharness4j.personal;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.task.TaskContext;
import io.openharness4j.task.TaskHandler;
import io.openharness4j.task.TaskResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class PersonalAgentTaskHandler implements TaskHandler {

    public static final String DEFAULT_TYPE = "openharness.personal.agent";
    public static final String INPUT_SESSION_ID = "sessionId";
    public static final String INPUT_USER_ID = "userId";
    public static final String INPUT_TEXT = "text";

    private final AgentRuntime runtime;
    private final String type;

    public PersonalAgentTaskHandler(AgentRuntime runtime) {
        this(runtime, DEFAULT_TYPE);
    }

    public PersonalAgentTaskHandler(AgentRuntime runtime, String type) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        this.type = type;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public TaskResult handle(TaskContext context) {
        String sessionId = text(context.input(), INPUT_SESSION_ID);
        String userId = text(context.input(), INPUT_USER_ID);
        String input = text(context.input(), INPUT_TEXT);
        AgentResponse response = runtime.run(new AgentRequest(sessionId, userId, input, context.metadata()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", response.traceId());
        data.put("finishReason", response.finishReason().name());
        data.put("toolCallCount", response.toolCalls().size());
        data.put("promptTokens", response.usage().promptTokens());
        data.put("completionTokens", response.usage().completionTokens());
        data.put("totalTokens", response.usage().totalTokens());
        if (response.finishReason() == FinishReason.STOP) {
            return TaskResult.success(response.content(), data);
        }
        return new TaskResult(
                response.content(),
                data,
                "AGENT_FINISHED_WITH_" + response.finishReason().name(),
                "agent runtime finished with " + response.finishReason()
        );
    }

    private static String text(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("task input missing string field: " + key);
    }
}

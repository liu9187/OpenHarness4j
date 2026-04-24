# OpenHarness4j

OpenHarness4j is a Java Agent Harness runtime for building controlled tool-calling agents.

The v0.1 scope focuses on a minimal embeddable runtime:

* Agent Loop
* LLM Adapter contract
* Tool registration and execution
* Permission hook before tool execution
* Single-run context handling
* Basic traceId, usage and tool-call observability

## Modules

| Module | Purpose |
| --- | --- |
| `api` | Shared public contracts and records |
| `llm-adapter` | `LLMAdapter`, mock adapter, OpenAI-compatible HTTP adapter |
| `tool-engine` | `Tool`, `ToolRegistry`, in-memory registry |
| `permission-engine` | Permission checker interfaces and defaults |
| `observability` | Trace container and tracer |
| `agent-runtime` | Default Agent Loop implementation |
| `starter` | Spring Boot auto-configuration |
| `examples` | Minimal runnable usage example |

## Minimal Usage

```java
InMemoryToolRegistry registry = new InMemoryToolRegistry();
registry.register(new MyTool());

LLMAdapter llmAdapter = new MyLLMAdapter();
AgentRuntime runtime = new DefaultAgentRuntime(llmAdapter, registry);

AgentResponse response = runtime.run(
        AgentRequest.of("session-1", "user-1", "do something")
);
```

## OpenAI-Compatible Adapter

For OpenAI-compatible `/v1/chat/completions` endpoints:

```java
LLMAdapter llmAdapter = new OpenAICompatibleLLMAdapter(
        "https://api.openai.com/v1/chat/completions",
        System.getenv("OPENAI_API_KEY"),
        System.getenv("OPENAI_MODEL")
);
```

For local compatible providers such as Ollama, use the local endpoint and omit the API key:

```java
LLMAdapter llmAdapter = new OpenAICompatibleLLMAdapter(
        "http://localhost:11434/v1/chat/completions",
        null,
        "llama3.1"
);
```

## Build

```bash
mvn test
```

## Example

```bash
mvn -pl examples -am package exec:java
```

The example uses `MockLLMAdapter`, so it does not call an external model provider.

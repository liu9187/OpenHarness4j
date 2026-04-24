# OpenHarness4j

OpenHarness4j is a Java Agent Harness runtime for building controlled tool-calling agents.

The v0.2 scope adds cross-request memory on top of the v0.1 embeddable runtime:

* Agent Loop
* LLM Adapter contract
* Tool registration and execution
* Permission hook before tool execution
* Single-run context handling
* Basic traceId, usage and tool-call observability
* Cross-request session memory
* Context window trimming and simple summarization

## Status

`v0.2-memory` is complete for the Memory scope described in `产品文档.md`.

Current release guarantees:

* Embeddable Java `AgentRuntime`
* Mock and OpenAI-compatible LLM adapters
* Tool registry and structured tool execution
* Permission hook before every tool execution
* Deterministic handling for common runtime failures
* MemoryStore implementations for InMemory, Redis and MySQL/JDBC
* Memory-aware context manager for cross-request session history
* Context window trimming with optional summarization
* Spring Boot auto-configuration
* Runnable examples and verification tests

## Modules

| Module | Purpose |
| --- | --- |
| `api` | Shared public contracts and records |
| `llm-adapter` | `LLMAdapter`, mock adapter, OpenAI-compatible HTTP adapter |
| `tool-engine` | `Tool`, `ToolRegistry`, in-memory registry |
| `permission-engine` | Permission checker interfaces and defaults |
| `observability` | Trace container and tracer |
| `agent-runtime` | Default Agent Loop implementation |
| `memory-engine` | Cross-request memory, stores, window policy and summarization |
| `starter` | Spring Boot auto-configuration |
| `examples` | Runnable examples and v0.2 feature verification |

## Minimal Usage

Maven dependency for a plain Java integration:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-agent-runtime</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

```java
InMemoryToolRegistry registry = new InMemoryToolRegistry();
registry.register(new MyTool());

LLMAdapter llmAdapter = new MyLLMAdapter();
AgentRuntime runtime = new DefaultAgentRuntime(llmAdapter, registry);

AgentResponse response = runtime.run(
        AgentRequest.of("session-1", "user-1", "do something")
);
```

## Memory

Plain Java integration:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-memory-engine</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

```java
MemoryStore store = new InMemoryMemoryStore();
ContextManager contextManager = new MemoryContextManager(
        store,
        new MemoryWindowPolicy(20, true, new SimpleMemorySummarizer())
);

AgentRuntime runtime = new DefaultAgentRuntime(
        llmAdapter,
        registry,
        permissionChecker,
        agentTracer,
        contextManager,
        AgentRuntimeConfig.defaults()
);
```

Available stores:

* `InMemoryMemoryStore`
* `RedisMemoryStore`
* `MySqlMemoryStore`
* `JdbcMemoryStore`

## Spring Boot Starter

Maven dependency:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-spring-boot-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

Provide an `LLMAdapter` bean and optionally register tools or custom permission logic:

```java
@Bean
LLMAdapter llmAdapter() {
    return new OpenAICompatibleLLMAdapter(
            "http://localhost:11434/v1/chat/completions",
            null,
            "llama3.1"
    );
}

@Bean
Tool echoTool() {
    return new EchoTool();
}

@Bean
PermissionChecker permissionChecker() {
    return new DenyListPermissionChecker(Set.of("shell"));
}
```

Configure the loop guard:

```yaml
openharness:
  agent:
    max-iterations: 8
  memory:
    enabled: true
    max-messages: 20
    summarize-overflow: true
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

## Release Verification

Run the complete build and v0.2 feature verification:

```bash
mvn test
mvn -q -pl examples -am package exec:java
```

## Example

```bash
mvn -pl examples -am package exec:java
```

The default example uses `MockLLMAdapter`, so it does not call an external model provider.
It verifies the current v0.2 behavior:

* Text-only LLM response
* Single tool call
* Multiple tool calls in one LLM turn
* Permission denial before tool execution
* Missing tool recovery with `TOOL_NOT_FOUND`
* Invalid tool arguments with `INVALID_ARGS`
* Tool execution failure with `TOOL_EXECUTION_FAILED`
* Empty LLM response with deterministic `ERROR`
* Usage aggregation across LLM turns
* Max-iteration guard
* Cross-request session memory

For the minimal echo-only walkthrough:

```bash
mvn -pl examples -am package exec:java -Dexec.mainClass=io.openharness4j.examples.SimpleAgentExample
```

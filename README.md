# OpenHarness4j

OpenHarness4j is a Java Agent Harness runtime for building controlled, observable, tool-calling agents that can be embedded in business systems.

## Status

`v1.5-personal-agent-team-runtime` is complete for the optional personal agent and long-lived team runtime scope described in `产品文档.md`.

Parity note: v1.5 completes OpenHarness4j's Java embedded-runtime execution, standard toolkit/governance, markdown skill, context file, provider profile, CLI/dry-run and optional personal-agent/team-runtime slices. See [docs/openharness-comparison.md](docs/openharness-comparison.md) for the gap analysis and deliberately deferred product-level scope.

Current release guarantees:

* Embeddable Java `AgentRuntime`
* Mock, fallback and OpenAI-compatible LLM adapters
* Tool registry and structured tool execution
* Standard toolkit module with governed File, Shell, Web Fetch, Search and MCP Client tools
* Policy-based permission checks before every tool execution
* Permission audit events with in-memory audit store
* PreToolUse / PostToolUse hooks with approval abstraction
* Path-level and command-level governance policies
* Deterministic handling for common runtime failures
* Streaming runtime events for LLM attempts, retries, text deltas, tool lifecycle, cost updates and completion
* Configurable LLM and tool retry policies
* Optional parallel execution for independent tool calls while preserving result order
* Token-pricing based cost estimation
* Cross-request memory with InMemory, Redis and MySQL/JDBC stores
* Context file conventions for CLAUDE.md and MEMORY.md
* Explicit memory session resume, replace, append and clear APIs
* Skill engine for Prompt + Workflow definitions
* YAML and Markdown skill loading, including Anthropic-style skill front matter
* Task engine for async tasks, status query, cancellation and timeout
* Multi-Agent runtime with planning, sub-agent execution, aggregation and conflict detection
* Optional personal-agent module with channel adapters, workspace, history, background task status and audit
* Team runtime with long-lived agent registry, spawn, query, cancellation and result archive
* Plugin engine with descriptor, activation lifecycle and registry contributions
* Provider profiles with environment-based model/API key resolution and fallback order
* CLI prompt, interactive, JSON/stream-json and dry-run readiness checks
* Observation export for finished runtime traces
* Spring Boot auto-configuration
* Runnable examples and verification tests
* Full usage guide in [docs/usage.md](docs/usage.md) and CLI guide in [docs/cli.md](docs/cli.md)

## Modules

| Module | Purpose |
| --- | --- |
| `api` | Shared public contracts and records |
| `llm-adapter` | `LLMAdapter`, mock adapter, fallback adapter, registry, OpenAI-compatible HTTP adapter and provider profiles |
| `tool-engine` | `Tool`, `ToolRegistry`, in-memory registry |
| `toolkit-engine` | Governed File, Shell, Web Fetch, Search and MCP Client tools |
| `permission-engine` | Permission policy, checker, audit event and audit store |
| `observability` | Trace container, observation exporter and exporting tracer |
| `agent-runtime` | Default Agent Loop implementation, streaming events, retry, parallel tools and cost tracking |
| `memory-engine` | Cross-request memory, stores, window policy, summarization, context files and session manager |
| `skill-engine` | Prompt + Workflow skills with Java DSL, YAML loading and Markdown loading |
| `task-engine` | In-memory async task execution, status, cancellation and timeout |
| `multi-agent` | Planning, sub-agent registry, aggregation and conflict detection |
| `personal-agent` | Personal agent channels, workspace/history/audit and long-lived team runtime |
| `plugin-engine` | Plugin descriptors, activation lifecycle and contribution context |
| `starter` | Spring Boot auto-configuration |
| `cli` | Prompt, interactive, JSON/stream-json and dry-run command surface |
| `examples` | Runnable examples and v1.5 feature verification |

## Minimal Usage

Maven dependency for a plain Java integration:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-agent-runtime</artifactId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
```

Add `openharness-toolkit-engine` as well when using the standard toolkit tools.

```java
InMemoryToolRegistry registry = new InMemoryToolRegistry();
registry.register(new MyTool());

LLMAdapter llmAdapter = new OpenAICompatibleLLMAdapter(
        "https://api.openai.com/v1/chat/completions",
        System.getenv("OPENAI_API_KEY"),
        System.getenv("OPENAI_MODEL")
);

PermissionAuditStore auditStore = new InMemoryPermissionAuditStore();
PermissionChecker permissionChecker = new AuditingPermissionChecker(
        new PolicyPermissionChecker(PermissionPolicy.allowByDefault(List.of())),
        auditStore
);

ObservationExporter exporter = new InMemoryObservationExporter();
AgentRuntime runtime = new DefaultAgentRuntime(
        llmAdapter,
        registry,
        permissionChecker,
        new ExportingAgentTracer(exporter),
        new DefaultContextManager(),
        AgentRuntimeConfig.defaults()
);

AgentResponse response = runtime.run(
        AgentRequest.of("session-1", "user-1", "do something")
);
```

Runtime execution parity features can be enabled through `AgentRuntimeConfig`:

```java
List<AgentEvent> events = new CopyOnWriteArrayList<>();

AgentRuntimeConfig config = AgentRuntimeConfig.defaults()
        .withLlmRetryPolicy(RetryPolicy.fixedDelay(2, 100))
        .withToolRetryPolicy(RetryPolicy.fixedDelay(2, 100))
        .withParallelToolExecution(true)
        .withCostEstimator(new TokenPricingCostEstimator(
                "USD",
                new BigDecimal("1.00"),
                new BigDecimal("2.00")
        ));

AgentRuntime runtime = new DefaultAgentRuntime(
        llmAdapter,
        registry,
        permissionChecker,
        new ExportingAgentTracer(exporter),
        new DefaultContextManager(),
        config
);

AgentResponse response = runtime.run(
        AgentRequest.of("session-1", "user-1", "do something"),
        events::add
);
```

Toolkit and governance can be composed with runtime hooks:

```java
Path base = Path.of("/srv/agent-workspace");

PathAccessPolicy pathPolicy = PathAccessPolicy.denyByDefault(List.of(
        PathAccessRule.allow(base, EnumSet.allOf(PathAccessMode.class))
));

CommandPermissionPolicy commandPolicy = CommandPermissionPolicy.denyByDefault(List.of(
        CommandPermissionRule.denyContains("rm -rf", RiskLevel.HIGH, "destructive command"),
        CommandPermissionRule.allowPrefix("printf ")
));

InMemoryToolRegistry registry = new InMemoryToolRegistry();
registry.register(new FileTool(base, pathPolicy));
registry.register(new ShellTool(base, commandPolicy));
registry.register(new SearchTool(searchProvider));
registry.register(new McpClientTool(mcpClient));

ToolExecutionHook approvalHook = new ApprovalRequiredToolHook(
        Set.of("shell"),
        RiskLevel.HIGH,
        "shell approval required",
        approvalHandler
);
```

## Personal Agent And Team Runtime

Add the optional personal-agent module for channel-facing assistants and long-lived team work:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-personal-agent</artifactId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
```

```java
try (DefaultPersonalAgentService personalAgent = new DefaultPersonalAgentService(runtime)) {
    PersonalAgentMessage message = new SlackChannelAdapter().toMessage(Map.of(
            "channel_id", "C123",
            "user_id", "U123",
            "text", "prepare weekly brief"
    ));

    PersonalAgentSubmission submission = personalAgent.submit(message);
    PersonalAgentTaskSnapshot snapshot = personalAgent.get(submission.taskId()).orElseThrow();
}

InMemoryTeamAgentRegistry teamRegistry = new InMemoryTeamAgentRegistry();
teamRegistry.register(new TeamAgentDefinition("researcher", "Research", researcherRuntime));

try (InMemoryTeamRuntime teamRuntime = new InMemoryTeamRuntime(teamRegistry)) {
    TeamAgentSubmission spawned = teamRuntime.spawn(TeamAgentRequest.of(
            "researcher",
            "session-1",
            "user-1",
            "collect facts"
    ));
    TeamAgentSnapshot result = teamRuntime.get(spawned.taskId()).orElseThrow();
    // Poll until result.status().terminal() before archiving in production code.
    TeamAgentArchive archive = teamRuntime.archive(spawned.taskId()).orElseThrow();
}
```

## Spring Boot Starter

Maven dependency:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-spring-boot-starter</artifactId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
```

Provide an `LLMAdapter` bean or enable provider profiles, then optionally register tools, skills, tasks, sub-agents or plugins:

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
```

Production-oriented configuration:

```yaml
openharness:
  agent:
    max-iterations: 8
    parallel-tool-execution: true
    llm-retry-max-attempts: 2
    llm-retry-backoff-millis: 100
    tool-retry-max-attempts: 2
    tool-retry-backoff-millis: 100
  permission:
    default-allow: true
    denied-tools:
      - shell
  toolkit:
    base-directory: /srv/agent-workspace
    file:
      enabled: true
      allowed-paths:
        - .
      denied-paths:
        - secrets
    shell:
      enabled: true
      allowed-prefixes:
        - "printf "
      denied-contains:
        - "rm -rf"
      default-timeout-millis: 10000
    web-fetch:
      enabled: true
    search:
      enabled: true
    mcp:
      enabled: true
  memory:
    enabled: true
    max-messages: 20
    summarize-overflow: true
    context-files:
      enabled: true
      base-directory: .
      load-claude: true
      load-memory: true
      persist-memory: true
  skill:
    enabled: true
    markdown-locations:
      - classpath*:openharness/skills/*.md
      - classpath*:openharness/skills/*/SKILL.md
  task:
    enabled: true
    default-timeout-millis: 30000
    pool-size: 4
  multi-agent:
    enabled: true
  plugin:
    enabled: true
  provider:
    enabled: true
    default-profile: openai
    fallback-order:
      - openai
      - local
    profiles:
      - name: openai
        endpoint: https://api.openai.com/v1/chat/completions
        api-key-env: OPENAI_API_KEY
        model-env: OPENAI_MODEL
      - name: local
        endpoint: http://localhost:11434/v1/chat/completions
        model: llama3.1
```

## Compatibility

OpenHarness4j v1.5 keeps the public contracts in the modules below source-compatible within the `1.5.x` snapshot line unless a breaking change is explicitly documented:

* `api`
* `agent-runtime`
* `llm-adapter`
* `tool-engine`
* `toolkit-engine`
* `permission-engine`
* `observability`
* `memory-engine`
* `skill-engine`
* `task-engine`
* `multi-agent`
* `personal-agent`
* `plugin-engine`
* `starter`

Production integrations should depend on public interfaces and records, not internal implementation classes, when possible.

## Release Verification

Run the complete build and v1.5 feature verification:

```bash
mvn test
mvn -q -pl examples -am package exec:java
```

The verification entry covers:

* Text-only LLM response
* Single and multiple tool calls
* Permission denial before tool execution
* Missing tool recovery
* Invalid tool arguments
* Tool execution failure
* Empty LLM response
* Usage aggregation
* Runtime streaming events, retry, parallel tools and cost tracking
* Toolkit and governance parity with File, Shell, Search, MCP, path rules, command rules and approval hooks
* Max-iteration guard
* Cross-request session memory
* Context file loading, persisted MEMORY.md and session auto-compact
* Skill workflow
* Markdown skill loading
* Task engine status, cancellation and timeout
* Multi-Agent planning, execution, aggregation and conflict detection
* Personal agent Slack-channel submission, workspace/history/audit and team runtime spawn/query/archive
* Production runtime audit, plugin, fallback model and observation export
* Provider profile selection and fallback
* CLI prompt, stream-json, interactive mode and dry-run readiness

For the minimal echo-only walkthrough:

```bash
mvn -pl examples -am package exec:java -Dexec.mainClass=io.openharness4j.examples.SimpleAgentExample
```

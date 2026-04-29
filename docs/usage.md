# OpenHarness4j Usage Guide

This guide covers the production-oriented Java and Spring Boot entry points available in `1.5.0-SNAPSHOT`.

## Maven Dependencies

Plain Java runtime:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-agent-runtime</artifactId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
```

Spring Boot integration:

```xml
<dependency>
    <groupId>io.openharness4j</groupId>
    <artifactId>openharness-spring-boot-starter</artifactId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
```

Add these modules only when needed:

| Module | Use when |
| --- | --- |
| `openharness-toolkit-engine` | You want governed File, Shell, Web Fetch, Search or MCP tools |
| `openharness-memory-engine` | You want custom memory stores or explicit memory/session APIs |
| `openharness-skill-engine` | You want Java/YAML/Markdown skills outside the starter |
| `openharness-task-engine` | You want in-process async task execution |
| `openharness-multi-agent` | You want planning plus sub-agent execution |
| `openharness-personal-agent` | You want channel-facing personal agents or long-lived team runtime |

## Plain Java Runtime

```java
InMemoryToolRegistry tools = new InMemoryToolRegistry();
tools.register(new MyBusinessTool());

LLMAdapter llm = new OpenAICompatibleLLMAdapter(
        "https://api.openai.com/v1/chat/completions",
        System.getenv("OPENAI_API_KEY"),
        System.getenv("OPENAI_MODEL")
);

PermissionAuditStore auditStore = new InMemoryPermissionAuditStore();
PermissionChecker permissions = new AuditingPermissionChecker(
        new PolicyPermissionChecker(PermissionPolicy.allowByDefault(List.of())),
        auditStore
);

AgentRuntimeConfig config = AgentRuntimeConfig.defaults()
        .withLlmRetryPolicy(RetryPolicy.fixedDelay(2, 100))
        .withToolRetryPolicy(RetryPolicy.fixedDelay(2, 100))
        .withParallelToolExecution(true);

AgentRuntime runtime = new DefaultAgentRuntime(
        llm,
        tools,
        permissions,
        new ExportingAgentTracer(new InMemoryObservationExporter()),
        new DefaultContextManager(),
        config
);

AgentResponse response = runtime.run(
        AgentRequest.of("session-1", "user-1", "summarize today's work")
);
```

Use `runtime.run(request, eventSink)` to consume typed runtime events such as LLM attempts, retries, text deltas, tool lifecycle events, cost updates and completion.

## Governed Toolkit

```java
Path workspace = Path.of("/srv/agent-workspace");

PathAccessPolicy pathPolicy = PathAccessPolicy.denyByDefault(List.of(
        PathAccessRule.allow(workspace, EnumSet.allOf(PathAccessMode.class)),
        PathAccessRule.deny(
                workspace.resolve("secrets"),
                EnumSet.allOf(PathAccessMode.class),
                RiskLevel.HIGH,
                "secret path denied"
        )
));

CommandPermissionPolicy commandPolicy = CommandPermissionPolicy.denyByDefault(List.of(
        CommandPermissionRule.allowPrefix("printf "),
        CommandPermissionRule.denyContains("rm -rf", RiskLevel.HIGH, "destructive command")
));

InMemoryToolRegistry tools = new InMemoryToolRegistry();
tools.register(new FileTool(workspace, pathPolicy));
tools.register(new ShellTool(workspace, commandPolicy));
tools.register(new SearchTool(searchProvider));
tools.register(new McpClientTool(mcpClient));
```

For interactive governance, compose `ApprovalRequiredToolHook` into `DefaultAgentRuntime` or expose a `ToolExecutionHook` bean in Spring Boot.

## Markdown Skills

Markdown skills are regular `.md` files with optional front matter. A minimal skill can rely on the markdown body as its LLM prompt:

```markdown
---
name: Incident Summary
description: Summarize an incident timeline.
---
Summarize the following incident notes and call out unresolved risks:

{{notes}}
```

Workflow skills can declare tool steps:

```markdown
---
name: Release Note Skill
version: 1.3.0
requiredTools:
  - file
workflow:
  - name: read_notes
    type: tool
    tool: file
    args:
      operation: read
      path: "{{path}}"
  - name: summarize
    type: llm
    prompt: "Create release notes from {{steps.read_notes.output}}."
---
```

Plain Java loading:

```java
SkillDefinition skill = new MarkdownSkillLoader().load(Path.of("skills/release/SKILL.md"));
InMemorySkillRegistry skills = new InMemorySkillRegistry();
skills.register(skill);
```

Spring Boot loads markdown skills by default from:

```yaml
openharness:
  skill:
    markdown-locations:
      - classpath*:openharness/skills/*.md
      - classpath*:openharness/skills/*/SKILL.md
```

## Context Files And Memory

`ContextFileContextManager` discovers `CLAUDE.md` and `MEMORY.md` by walking upward from a configured base directory. `CLAUDE.md` is injected as project instructions. `MEMORY.md` is injected as persistent memory and can be rewritten on completion when enabled.

```java
MemoryStore memoryStore = new InMemoryMemoryStore();
ContextManager context = new ContextFileContextManager(
        new MemoryContextManager(
                memoryStore,
                new MemoryWindowPolicy(20, true, new SimpleMemorySummarizer())
        ),
        Path.of("."),
        true,
        true,
        true,
        new SimpleMemorySummarizer()
);

MemorySessionManager sessions = new MemorySessionManager(memoryStore);
List<Message> history = sessions.resume("session-1");
```

Spring Boot configuration:

```yaml
openharness:
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
```

## Provider Profiles

Provider profiles create OpenAI-compatible adapters from configuration and can select a default/fallback chain.

```yaml
openharness:
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

When `openharness.provider.enabled=true` and no custom `LLMAdapter` bean exists, the starter creates an `LLMAdapter` from the configured profiles. It also registers enabled profiles in `LLMAdapterRegistry`.

Plain Java selection:

```java
InMemoryLLMAdapterRegistry registry = new LLMProviderProfileFactory().registry(profiles);
LLMAdapter adapter = new LLMProviderProfileSelector("openai", List.of("openai", "local"))
        .select(registry)
        .orElseThrow();
```

## Personal Agent And Team Runtime

`openharness-personal-agent` is an optional upper layer over `AgentRuntime` and `task-engine`. It keeps channel payload handling, personal workspace/history, background task status and audit outside the core runtime.

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
```

The module ships payload adapters for direct, Feishu, Slack, Telegram and Discord channels. For production storage, implement the workspace, history, task and audit store interfaces; the default implementation is in-memory.

Team runtime manages long-lived sub agents through a team registry and task lifecycle:

```java
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

## Verification

Run the full build and feature verifier:

```bash
mvn test
mvn -q -pl examples -am package exec:java
```

The example verifier should end with:

```text
All 19 verification scenarios passed.
```

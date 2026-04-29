# OpenHarness4j CLI

The `openharness-cli` module provides a small `oh`-style command surface for local verification, scripted prompts and dry-run readiness checks.

## Prompt Mode

```bash
mvn -q -pl cli -am exec:java -Dexec.args="-p hello --mock-response 'cli ok'"
```

Plain text is the default output. Use `--output json` for structured final responses or `--output stream-json` for runtime events plus the final response.

```bash
mvn -q -pl cli -am exec:java -Dexec.args="-p hello --mock-response 'stream ok' --output stream-json"
```

## Interactive Mode

```bash
mvn -q -pl cli -am exec:java -Dexec.args="--interactive --mock-response 'interactive ok'"
```

Type `exit` to leave the prompt loop.

## Dry Run

Dry run checks configuration without calling an LLM or executing tools.

```bash
mvn -q -pl cli -am exec:java -Dexec.args="--dry-run --mock-response ready --enable-tool echo --tool echo --output json"
```

The report includes provider readiness, requested tools, skill loading, permission decisions, MCP risk notes and next actions. A non-zero exit code means the runtime is not ready for the requested operation.

Common checks:

```bash
mvn -q -pl cli -am exec:java -Dexec.args="--dry-run --enable-tool shell,mcp_call --deny-tool shell --tool shell --skill missing_skill --output json"
```

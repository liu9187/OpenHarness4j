package io.openharness4j.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenHarnessCliTest {

    @TempDir
    Path tempDirectory;

    @Test
    void runsPromptWithMockResponseAsPlainText() {
        CliResult result = run("-p", "hello", "--mock-response", "cli ok");

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("cli ok"));
        assertEquals("", result.err());
    }

    @Test
    void writesStreamJsonEvents() {
        CliResult result = run("-p", "hello", "--mock-response", "stream ok", "--output", "stream-json");

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("\"type\":\"STARTED\""));
        assertTrue(result.out().contains("\"type\":\"TEXT_DELTA\""));
        assertTrue(result.out().contains("\"type\":\"FINAL_RESPONSE\""));
        assertTrue(result.out().contains("stream ok"));
    }

    @Test
    void dryRunPassesWithMockProviderAndKnownSkill() throws Exception {
        Path skill = tempDirectory.resolve("known.md");
        Files.writeString(skill, """
                ---
                id: known_skill
                name: Known Skill
                ---
                Say hello to {{input}}.
                """);

        CliResult result = run(
                "--dry-run",
                "--mock-response", "ready",
                "--enable-tool", "echo",
                "--tool", "echo",
                "--skill", "known_skill",
                "--skill-location", skill.toString(),
                "--output", "json"
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("\"ready\":true"));
        assertTrue(result.out().contains("known_skill"));
    }

    @Test
    void dryRunReportsMissingProviderUnknownSkillDeniedToolAndMcpRisk() {
        CliResult result = run(
                "--dry-run",
                "--enable-tool", "shell,mcp_call",
                "--deny-tool", "shell",
                "--tool", "shell",
                "--skill", "missing_skill",
                "--output", "json"
        );

        assertEquals(2, result.exitCode());
        assertTrue(result.out().contains("\"ready\":false"));
        assertTrue(result.out().contains("missing provider"));
        assertTrue(result.out().contains("unknown skill"));
        assertTrue(result.out().contains("denied by policy"));
        assertTrue(result.out().contains("MCP tool is enabled"));
    }

    @Test
    void interactiveModeReadsPromptsUntilExit() {
        CliResult result = runWithInput(
                "hello\nexit\n",
                "--interactive",
                "--mock-response", "interactive ok"
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("oh> "));
        assertTrue(result.out().contains("interactive ok"));
    }

    private CliResult run(String... args) {
        return runWithInput("", args);
    }

    private CliResult runWithInput(String input, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        InputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        int exitCode = new OpenHarnessCli().run(
                args,
                in,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );
        return new CliResult(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record CliResult(int exitCode, String out, String err) {
    }
}

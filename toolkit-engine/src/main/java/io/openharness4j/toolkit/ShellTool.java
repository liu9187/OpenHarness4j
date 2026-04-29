package io.openharness4j.toolkit;

import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.permission.CommandPermissionPolicy;
import io.openharness4j.tool.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ShellTool implements Tool {

    public static final String NAME = "shell";

    private final Path workingDirectory;
    private final CommandPermissionPolicy commandPermissionPolicy;
    private final List<String> shellPrefix;
    private final long defaultTimeoutMillis;

    public ShellTool(Path workingDirectory, CommandPermissionPolicy commandPermissionPolicy) {
        this(workingDirectory, commandPermissionPolicy, List.of("/bin/sh", "-c"), Duration.ofSeconds(10).toMillis());
    }

    public ShellTool(
            Path workingDirectory,
            CommandPermissionPolicy commandPermissionPolicy,
            List<String> shellPrefix,
            long defaultTimeoutMillis
    ) {
        this.workingDirectory = workingDirectory == null
                ? Path.of(".").toAbsolutePath().normalize()
                : workingDirectory.toAbsolutePath().normalize();
        this.commandPermissionPolicy = commandPermissionPolicy == null
                ? CommandPermissionPolicy.denyByDefault(List.of())
                : commandPermissionPolicy;
        this.shellPrefix = shellPrefix == null || shellPrefix.isEmpty()
                ? List.of("/bin/sh", "-c")
                : List.copyOf(shellPrefix);
        this.defaultTimeoutMillis = defaultTimeoutMillis <= 0 ? Duration.ofSeconds(10).toMillis() : defaultTimeoutMillis;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Execute governed shell commands with timeout and command-level policy checks.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            String command = ToolArgs.requiredString(context.args(), "command");
            PermissionDecision decision = commandPermissionPolicy.allow(command);
            if (!decision.allowed()) {
                return ToolResult.permissionDenied(decision.reason());
            }
            long timeoutMillis = ToolArgs.longValue(context.args(), "timeoutMillis", defaultTimeoutMillis);
            ProcessBuilder builder = new ProcessBuilder(commandLine(command));
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ToolResult.failed("COMMAND_TIMEOUT", "command exceeded timeout: " + timeoutMillis + "ms");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return ToolResult.failed("COMMAND_FAILED", "exit=" + process.exitValue() + "\n" + output);
            }
            return ToolResult.success(output, Map.of("exitCode", process.exitValue(), "command", command));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed("INVALID_ARGS", safeMessage(ex));
        } catch (IOException ex) {
            return ToolResult.failed("COMMAND_IO_ERROR", safeMessage(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResult.failed("COMMAND_INTERRUPTED", "command interrupted");
        }
    }

    private List<String> commandLine(String command) {
        java.util.ArrayList<String> line = new java.util.ArrayList<>(shellPrefix);
        line.add(command);
        return line;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}

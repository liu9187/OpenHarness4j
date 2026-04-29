package io.openharness4j.toolkit;

import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.permission.PathAccessMode;
import io.openharness4j.permission.PathAccessPolicy;
import io.openharness4j.tool.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FileTool implements Tool {

    public static final String NAME = "file";

    private final Path baseDirectory;
    private final PathAccessPolicy pathAccessPolicy;

    public FileTool(Path baseDirectory, PathAccessPolicy pathAccessPolicy) {
        this.baseDirectory = baseDirectory == null
                ? Path.of(".").toAbsolutePath().normalize()
                : baseDirectory.toAbsolutePath().normalize();
        this.pathAccessPolicy = pathAccessPolicy == null
                ? PathAccessPolicy.denyByDefault(List.of())
                : pathAccessPolicy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Read, write, append, delete, check or list files inside a governed base directory.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            String operation = ToolArgs.string(context.args(), "operation", "read").trim().toLowerCase();
            Path target = resolve(ToolArgs.requiredString(context.args(), "path"));
            return switch (operation) {
                case "read" -> read(target);
                case "write" -> write(target, ToolArgs.string(context.args(), "content", ""), false);
                case "append" -> write(target, ToolArgs.string(context.args(), "content", ""), true);
                case "delete" -> delete(target);
                case "exists" -> exists(target);
                case "list" -> list(target);
                default -> ToolResult.failed("UNSUPPORTED_FILE_OPERATION", "unsupported file operation: " + operation);
            };
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed("INVALID_ARGS", safeMessage(ex));
        } catch (IOException ex) {
            return ToolResult.failed("FILE_IO_ERROR", safeMessage(ex));
        }
    }

    private ToolResult read(Path target) throws IOException {
        PermissionDecision decision = pathAccessPolicy.allow(target, PathAccessMode.READ);
        if (!decision.allowed()) {
            return ToolResult.permissionDenied(decision.reason());
        }
        return ToolResult.success(Files.readString(target), Map.of("path", target.toString()));
    }

    private ToolResult write(Path target, String content, boolean append) throws IOException {
        PermissionDecision decision = pathAccessPolicy.allow(target, PathAccessMode.WRITE);
        if (!decision.allowed()) {
            return ToolResult.permissionDenied(decision.reason());
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (append) {
            Files.writeString(target, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        return ToolResult.success("file written", Map.of("path", target.toString(), "bytes", content.length()));
    }

    private ToolResult delete(Path target) throws IOException {
        PermissionDecision decision = pathAccessPolicy.allow(target, PathAccessMode.DELETE);
        if (!decision.allowed()) {
            return ToolResult.permissionDenied(decision.reason());
        }
        boolean deleted = Files.deleteIfExists(target);
        return ToolResult.success("file delete completed", Map.of("path", target.toString(), "deleted", deleted));
    }

    private ToolResult exists(Path target) {
        PermissionDecision decision = pathAccessPolicy.allow(target, PathAccessMode.READ);
        if (!decision.allowed()) {
            return ToolResult.permissionDenied(decision.reason());
        }
        return ToolResult.success(String.valueOf(Files.exists(target)), Map.of("path", target.toString(), "exists", Files.exists(target)));
    }

    private ToolResult list(Path target) throws IOException {
        PermissionDecision decision = pathAccessPolicy.allow(target, PathAccessMode.LIST);
        if (!decision.allowed()) {
            return ToolResult.permissionDenied(decision.reason());
        }
        try (var stream = Files.list(target)) {
            List<String> entries = stream
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> path.getFileName().toString())
                    .toList();
            return ToolResult.success(String.join("\n", entries), Map.of("path", target.toString(), "entries", entries));
        }
    }

    private Path resolve(String path) {
        Path raw = Path.of(path);
        Path target = raw.isAbsolute() ? raw.normalize() : baseDirectory.resolve(raw).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("path is outside base directory: " + path);
        }
        return target;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}

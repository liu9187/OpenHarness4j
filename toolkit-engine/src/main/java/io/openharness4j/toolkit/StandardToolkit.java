package io.openharness4j.toolkit;

import io.openharness4j.permission.CommandPermissionPolicy;
import io.openharness4j.permission.PathAccessPolicy;
import io.openharness4j.tool.ToolRegistry;

import java.nio.file.Path;
import java.util.List;

public class StandardToolkit {

    private final Path baseDirectory;
    private final PathAccessPolicy pathAccessPolicy;
    private final CommandPermissionPolicy commandPermissionPolicy;
    private final SearchProvider searchProvider;
    private final McpClient mcpClient;

    public StandardToolkit(
            Path baseDirectory,
            PathAccessPolicy pathAccessPolicy,
            CommandPermissionPolicy commandPermissionPolicy,
            SearchProvider searchProvider,
            McpClient mcpClient
    ) {
        this.baseDirectory = baseDirectory;
        this.pathAccessPolicy = pathAccessPolicy;
        this.commandPermissionPolicy = commandPermissionPolicy;
        this.searchProvider = searchProvider;
        this.mcpClient = mcpClient;
    }

    public void registerAll(ToolRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        registry.register(new FileTool(baseDirectory, pathAccessPolicy));
        registry.register(new ShellTool(baseDirectory, commandPermissionPolicy));
        registry.register(new WebFetchTool());
        registry.register(new SearchTool(searchProvider));
        registry.register(new McpClientTool(mcpClient));
    }

    public static StandardToolkit safeDefaults(Path baseDirectory) {
        return new StandardToolkit(
                baseDirectory,
                PathAccessPolicy.denyByDefault(List.of()),
                CommandPermissionPolicy.denyByDefault(List.of()),
                new InMemorySearchProvider(List.of()),
                null
        );
    }
}

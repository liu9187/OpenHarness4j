package io.openharness4j.toolkit;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

import java.util.List;
import java.util.Map;

public class SearchTool implements Tool {

    public static final String NAME = "search";

    private final SearchProvider searchProvider;

    public SearchTool(SearchProvider searchProvider) {
        this.searchProvider = searchProvider == null ? (query, limit) -> List.of() : searchProvider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Run a governed search through a pluggable SearchProvider.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            String query = ToolArgs.requiredString(context.args(), "query");
            int limit = ToolArgs.intValue(context.args(), "limit", 5);
            List<SearchResult> results = searchProvider.search(query, limit);
            String content = results.stream()
                    .map(result -> result.title() + " - " + result.url() + "\n" + result.snippet())
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse("");
            return ToolResult.success(content, Map.of("query", query, "count", results.size(), "results", results));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed("INVALID_ARGS", safeMessage(ex));
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}

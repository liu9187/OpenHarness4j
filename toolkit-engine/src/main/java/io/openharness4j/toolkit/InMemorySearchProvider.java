package io.openharness4j.toolkit;

import java.util.List;

public class InMemorySearchProvider implements SearchProvider {

    private final List<SearchResult> results;

    public InMemorySearchProvider(List<SearchResult> results) {
        this.results = results == null ? List.of() : List.copyOf(results);
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        String normalized = query == null ? "" : query.toLowerCase();
        return results.stream()
                .filter(result -> result.title().toLowerCase().contains(normalized)
                        || result.snippet().toLowerCase().contains(normalized)
                        || result.url().toLowerCase().contains(normalized))
                .limit(Math.max(1, limit))
                .toList();
    }
}

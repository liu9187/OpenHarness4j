package io.openharness4j.toolkit;

import java.util.List;

@FunctionalInterface
public interface SearchProvider {

    List<SearchResult> search(String query, int limit);
}

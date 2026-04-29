package io.openharness4j.toolkit;

public record SearchResult(String title, String url, String snippet) {
    public SearchResult {
        title = title == null ? "" : title;
        url = url == null ? "" : url;
        snippet = snippet == null ? "" : snippet;
    }
}

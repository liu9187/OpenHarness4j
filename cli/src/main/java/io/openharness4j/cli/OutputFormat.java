package io.openharness4j.cli;

enum OutputFormat {
    PLAIN,
    JSON,
    STREAM_JSON;

    static OutputFormat from(String value) {
        if (value == null || value.isBlank()) {
            return PLAIN;
        }
        return switch (value.trim().toLowerCase()) {
            case "plain" -> PLAIN;
            case "json" -> JSON;
            case "stream-json", "stream_json", "streamjson" -> STREAM_JSON;
            default -> throw new IllegalArgumentException("unsupported output format: " + value);
        };
    }
}

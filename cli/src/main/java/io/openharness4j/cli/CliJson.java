package io.openharness4j.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class CliJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CliJson() {
    }

    static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize CLI JSON", ex);
        }
    }
}

package io.openharness4j.skill;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TemplateRenderer {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");
    private static final Pattern EXACT_TOKEN = Pattern.compile("^\\s*\\{\\{\\s*([^}]+?)\\s*}}\\s*$");

    private TemplateRenderer() {
    }

    static String renderString(String template, Map<String, Object> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            Object value = resolve(matcher.group(1), values);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(stringValue(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    static Map<String, Object> renderMap(Map<String, Object> template, Map<String, Object> values) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            rendered.put(entry.getKey(), renderValue(entry.getValue(), values));
        }
        return rendered;
    }

    private static Object renderValue(Object value, Map<String, Object> values) {
        if (value instanceof String string) {
            Matcher exact = EXACT_TOKEN.matcher(string);
            if (exact.matches()) {
                return resolve(exact.group(1), values);
            }
            return renderString(string, values);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                rendered.put(String.valueOf(entry.getKey()), renderValue(entry.getValue(), values));
            }
            return rendered;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> rendered = new ArrayList<>();
            for (Object item : iterable) {
                rendered.add(renderValue(item, values));
            }
            return rendered;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Object resolve(String path, Map<String, Object> values) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("template variable must not be blank");
        }
        Object current = values;
        for (String part : normalized.split("\\.")) {
            if (current instanceof Map<?, ?> map && map.containsKey(part)) {
                current = ((Map<String, Object>) map).get(part);
            } else {
                throw new IllegalArgumentException("missing template variable: " + normalized);
            }
        }
        if (current == null) {
            throw new IllegalArgumentException("missing template variable: " + normalized);
        }
        return current;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}

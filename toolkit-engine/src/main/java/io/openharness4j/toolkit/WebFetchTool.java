package io.openharness4j.toolkit;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class WebFetchTool implements Tool {

    public static final String NAME = "web_fetch";

    private final HttpClient httpClient;
    private final long defaultTimeoutMillis;
    private final int maxBytes;

    public WebFetchTool() {
        this(HttpClient.newHttpClient(), Duration.ofSeconds(10).toMillis(), 64_000);
    }

    public WebFetchTool(HttpClient httpClient, long defaultTimeoutMillis, int maxBytes) {
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.defaultTimeoutMillis = defaultTimeoutMillis <= 0 ? Duration.ofSeconds(10).toMillis() : defaultTimeoutMillis;
        this.maxBytes = maxBytes <= 0 ? 64_000 : maxBytes;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fetch an http or https URL with timeout and response size limiting.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            URI uri = URI.create(ToolArgs.requiredString(context.args(), "url"));
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return ToolResult.permissionDenied("only http and https URLs are allowed");
            }
            long timeoutMillis = ToolArgs.longValue(context.args(), "timeoutMillis", defaultTimeoutMillis);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            String clipped = body.length() > maxBytes ? body.substring(0, maxBytes) : body;
            return ToolResult.success(clipped, Map.of(
                    "url", uri.toString(),
                    "statusCode", response.statusCode(),
                    "truncated", body.length() > clipped.length()
            ));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed("INVALID_ARGS", safeMessage(ex));
        } catch (IOException ex) {
            return ToolResult.failed("WEB_FETCH_FAILED", safeMessage(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResult.failed("WEB_FETCH_INTERRUPTED", "web fetch interrupted");
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}

package io.openharness4j.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CliOptions {

    boolean help;
    boolean dryRun;
    boolean interactive;
    String prompt = "";
    String sessionId = "cli-session";
    String userId = "cli-user";
    OutputFormat outputFormat = OutputFormat.PLAIN;
    String mockResponse = "";
    String providerName = "default";
    String providerEndpoint = "";
    String providerApiKey = "";
    String providerApiKeyEnv = "";
    String providerModel = "";
    String providerModelEnv = "";
    String skillId = "";
    String mcpServer = "";
    Path baseDirectory = Path.of(".");
    final List<String> enabledTools = new ArrayList<>();
    final List<String> deniedTools = new ArrayList<>();
    final List<String> selectedTools = new ArrayList<>();
    final List<Path> skillLocations = new ArrayList<>();

    boolean hasPrompt() {
        return prompt != null && !prompt.isBlank();
    }

    boolean hasMockProvider() {
        return mockResponse != null && !mockResponse.isBlank();
    }

    boolean hasConfiguredProvider() {
        return !providerEndpoint.isBlank() && (!providerModel.isBlank() || !providerModelEnv.isBlank());
    }

    boolean hasAnyProvider() {
        return hasMockProvider() || hasConfiguredProvider();
    }
}

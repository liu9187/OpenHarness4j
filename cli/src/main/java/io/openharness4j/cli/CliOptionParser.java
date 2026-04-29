package io.openharness4j.cli;

import java.nio.file.Path;
import java.util.Arrays;

final class CliOptionParser {

    private CliOptionParser() {
    }

    static CliOptions parse(String[] args) {
        CliOptions options = new CliOptions();
        String[] safeArgs = args == null ? new String[0] : args;
        for (int index = 0; index < safeArgs.length; index++) {
            String arg = safeArgs[index];
            switch (arg) {
                case "-h", "--help" -> options.help = true;
                case "--dry-run" -> options.dryRun = true;
                case "-i", "--interactive" -> options.interactive = true;
                case "-p", "--prompt" -> options.prompt = value(safeArgs, ++index, arg);
                case "--session" -> options.sessionId = value(safeArgs, ++index, arg);
                case "--user" -> options.userId = value(safeArgs, ++index, arg);
                case "--output" -> options.outputFormat = OutputFormat.from(value(safeArgs, ++index, arg));
                case "--mock-response" -> options.mockResponse = value(safeArgs, ++index, arg);
                case "--provider-name" -> options.providerName = value(safeArgs, ++index, arg);
                case "--provider-endpoint" -> options.providerEndpoint = value(safeArgs, ++index, arg);
                case "--provider-api-key" -> options.providerApiKey = value(safeArgs, ++index, arg);
                case "--provider-api-key-env" -> options.providerApiKeyEnv = value(safeArgs, ++index, arg);
                case "--provider-model" -> options.providerModel = value(safeArgs, ++index, arg);
                case "--provider-model-env" -> options.providerModelEnv = value(safeArgs, ++index, arg);
                case "--enable-tool" -> addCsv(options.enabledTools, value(safeArgs, ++index, arg));
                case "--deny-tool" -> addCsv(options.deniedTools, value(safeArgs, ++index, arg));
                case "--tool" -> addCsv(options.selectedTools, value(safeArgs, ++index, arg));
                case "--skill" -> options.skillId = value(safeArgs, ++index, arg);
                case "--skill-location" -> options.skillLocations.add(Path.of(value(safeArgs, ++index, arg)));
                case "--base-directory" -> options.baseDirectory = Path.of(value(safeArgs, ++index, arg));
                case "--mcp-server" -> options.mcpServer = value(safeArgs, ++index, arg);
                default -> throw new IllegalArgumentException("unknown option: " + arg);
            }
        }
        return options;
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException("missing value for " + option);
        }
        return args[index];
    }

    private static void addCsv(java.util.List<String> values, String raw) {
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(values::add);
    }
}

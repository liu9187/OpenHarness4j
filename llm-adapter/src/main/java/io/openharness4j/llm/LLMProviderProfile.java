package io.openharness4j.llm;

public record LLMProviderProfile(
        String name,
        String endpoint,
        String apiKey,
        String apiKeyEnv,
        String model,
        String modelEnv,
        boolean enabled
) {
    public LLMProviderProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        apiKey = apiKey == null ? "" : apiKey;
        apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv;
        model = model == null ? "" : model;
        modelEnv = modelEnv == null ? "" : modelEnv;
    }

    public String resolvedApiKey(ProviderValueResolver resolver) {
        if (!apiKey.isBlank()) {
            return apiKey;
        }
        if (!apiKeyEnv.isBlank() && resolver != null) {
            String value = resolver.resolve(apiKeyEnv);
            return value == null ? "" : value;
        }
        return "";
    }

    public String resolvedModel(ProviderValueResolver resolver) {
        if (!model.isBlank()) {
            return model;
        }
        if (!modelEnv.isBlank() && resolver != null) {
            String value = resolver.resolve(modelEnv);
            return value == null ? "" : value;
        }
        return "";
    }
}

package com.company.feishuagent.runtime.config;

public record RuntimeRoutingConfig(
        Long agentId,
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        String fallbackModel,
        String dispatchMode,
        String manualSkill,
        double temperature,
        int maxTokens,
        String updatedBy) {

    public RuntimeRoutingConfig sanitized() {
        return new RuntimeRoutingConfig(
                agentId,
                provider,
                model,
                "",
                baseUrl,
                fallbackModel,
                dispatchMode,
                manualSkill,
                temperature,
                maxTokens,
                updatedBy);
    }
}

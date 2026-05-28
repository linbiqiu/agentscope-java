package com.company.feishuagent.runtime.service;

public record RuntimeTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int cacheCreationInputTokens,
        int cacheReadInputTokens) {

    public static RuntimeTokenUsage empty() {
        return new RuntimeTokenUsage(0, 0, 0, 0, 0);
    }
}

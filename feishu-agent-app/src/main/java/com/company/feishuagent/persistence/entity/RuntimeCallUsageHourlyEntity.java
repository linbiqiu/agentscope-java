package com.company.feishuagent.persistence.entity;

import java.time.OffsetDateTime;

public record RuntimeCallUsageHourlyEntity(
        OffsetDateTime hourBucket,
        Long agentId,
        String provider,
        String modelName,
        String orgKey,
        long requestCount,
        long successCount,
        long failureCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cacheCreationInputTokens,
        long cacheReadInputTokens,
        OffsetDateTime updatedAt) {}

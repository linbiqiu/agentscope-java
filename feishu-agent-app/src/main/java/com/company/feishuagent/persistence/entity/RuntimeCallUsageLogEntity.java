package com.company.feishuagent.persistence.entity;

import java.time.OffsetDateTime;

public record RuntimeCallUsageLogEntity(
        Long id,
        String traceId,
        Long agentId,
        String userId,
        String conversationId,
        String chatType,
        String actorOpenId,
        String actorMobile,
        String actorEmployeeNo,
        String actorName,
        String provider,
        String modelName,
        String orgKey,
        String sessionKey,
        String requestedSkill,
        String resolvedSkill,
        String callStatus,
        String errorMessage,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int cacheCreationInputTokens,
        int cacheReadInputTokens,
        OffsetDateTime calledAt,
        OffsetDateTime createdAt) {}

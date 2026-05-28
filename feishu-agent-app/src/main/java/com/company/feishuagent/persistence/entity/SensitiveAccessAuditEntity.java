package com.company.feishuagent.persistence.entity;

import java.time.OffsetDateTime;

public record SensitiveAccessAuditEntity(
        Long id,
        String traceId,
        String conversationId,
        String skillName,
        String fieldName,
        String accessReason,
        OffsetDateTime createdAt) {}

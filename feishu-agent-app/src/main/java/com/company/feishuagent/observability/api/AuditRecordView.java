package com.company.feishuagent.observability.api;

public record AuditRecordView(
        String traceId,
        String conversationId,
        String skillName,
        String fieldName,
        String accessReason,
        String createdAt) {}

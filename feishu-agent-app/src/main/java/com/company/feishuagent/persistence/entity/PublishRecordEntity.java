package com.company.feishuagent.persistence.entity;

import java.time.OffsetDateTime;

public record PublishRecordEntity(
        Long id,
        String targetType,
        Long targetId,
        String operatorId,
        String traceId,
        String changeSummaryJson,
        OffsetDateTime createdAt) {}

package com.company.feishuagent.persistence.entity;

import java.time.OffsetDateTime;

public record FeishuUserProfileEntity(
        String actorOpenId,
        String actorUnionId,
        String actorUserId,
        String actorName,
        String actorMobile,
        String actorEmployeeNo,
        String actorEmail,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}

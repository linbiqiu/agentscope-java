package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.FeishuUserProfileEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.user-profile.backend", havingValue = "memory")
public class InMemoryFeishuUserProfileRepository implements FeishuUserProfileRepository {

    private final Map<String, FeishuUserProfileEntity> storage = new ConcurrentHashMap<>();

    @Override
    public FeishuUserProfileEntity findByOpenId(String openId) {
        if (openId == null || openId.isBlank()) {
            return null;
        }
        return storage.get(openId);
    }

    @Override
    public FeishuUserProfileEntity findByUnionId(String unionId) {
        if (unionId == null || unionId.isBlank()) {
            return null;
        }
        return storage.values().stream()
                .filter(profile -> unionId.equals(profile.actorUnionId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public FeishuUserProfileEntity findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return storage.values().stream()
                .filter(profile -> userId.equals(profile.actorUserId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void upsert(FeishuUserProfileEntity entity) {
        if (entity == null || entity.actorOpenId() == null || entity.actorOpenId().isBlank()) {
            return;
        }
        storage.compute(
                entity.actorOpenId(),
                (key, existing) -> {
                    if (existing == null) {
                        return entity;
                    }
                    return new FeishuUserProfileEntity(
                            key,
                            firstNonBlank(entity.actorUnionId(), existing.actorUnionId()),
                            firstNonBlank(entity.actorUserId(), existing.actorUserId()),
                            firstNonBlank(entity.actorName(), existing.actorName()),
                            firstNonBlank(entity.actorMobile(), existing.actorMobile()),
                            firstNonBlank(entity.actorEmployeeNo(), existing.actorEmployeeNo()),
                            firstNonBlank(entity.actorEmail(), existing.actorEmail()),
                            entity.lastSeenAt(),
                            existing.createdAt(),
                            entity.updatedAt());
                });
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}

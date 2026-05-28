package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.SensitiveAccessAuditEntity;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(SensitiveAccessAuditRepository.class)
public class InMemorySensitiveAccessAuditRepository implements SensitiveAccessAuditRepository {

    private final List<SensitiveAccessAuditEntity> storage = new CopyOnWriteArrayList<>();

    @Override
    public void save(SensitiveAccessAuditEntity entity) {
        storage.add(entity);
    }

    @Override
    public List<SensitiveAccessAuditEntity> findByTraceOrSkill(String traceId, String skillName) {
        return storage.stream()
                .filter(item -> (traceId == null || traceId.equals(item.traceId()))
                        && (skillName == null || skillName.equals(item.skillName())))
                .toList();
    }

    @Override
    public List<SensitiveAccessAuditEntity> findByTraceIdsOrSkill(List<String> traceIds, String skillName) {
        return storage.stream()
                .filter(item -> (traceIds == null || traceIds.isEmpty() || traceIds.contains(item.traceId()))
                        && (skillName == null || skillName.equals(item.skillName())))
                .toList();
    }
}

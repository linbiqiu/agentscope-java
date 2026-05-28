package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.SensitiveAccessAuditEntity;
import java.util.List;

public interface SensitiveAccessAuditRepository {

    void save(SensitiveAccessAuditEntity entity);

    List<SensitiveAccessAuditEntity> findByTraceOrSkill(String traceId, String skillName);

    List<SensitiveAccessAuditEntity> findByTraceIdsOrSkill(List<String> traceIds, String skillName);
}

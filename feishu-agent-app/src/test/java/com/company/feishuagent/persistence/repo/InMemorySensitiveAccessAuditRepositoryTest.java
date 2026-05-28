package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.company.feishuagent.persistence.entity.SensitiveAccessAuditEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemorySensitiveAccessAuditRepositoryTest {

    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-22T17:00:00Z");

    @Test
    void findByTraceOrSkillAppliesAndSemanticsWhenBothProvided() {
        InMemorySensitiveAccessAuditRepository repository = new InMemorySensitiveAccessAuditRepository();
        repository.save(
                new SensitiveAccessAuditEntity(1L, "t1", "c1", "default", "mobile", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(2L, "t1", "c2", "other", "email", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(3L, "t2", "c3", "default", "phone", "reason", FIXED_TIME));

        List<SensitiveAccessAuditEntity> result = repository.findByTraceOrSkill("t1", "default");

        assertEquals(1, result.size());
        assertEquals("c1", result.get(0).conversationId());
    }

    @Test
    void findByTraceOrSkillFiltersByTraceWhenOnlyTraceProvided() {
        InMemorySensitiveAccessAuditRepository repository = new InMemorySensitiveAccessAuditRepository();
        repository.save(
                new SensitiveAccessAuditEntity(1L, "t1", "c1", "default", "mobile", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(2L, "t2", "c2", "other", "email", "reason", FIXED_TIME));

        List<SensitiveAccessAuditEntity> result = repository.findByTraceOrSkill("t1", null);

        assertEquals(1, result.size());
        assertEquals("t1", result.get(0).traceId());
    }

    @Test
    void findByTraceOrSkillFiltersBySkillWhenOnlySkillProvided() {
        InMemorySensitiveAccessAuditRepository repository = new InMemorySensitiveAccessAuditRepository();
        repository.save(
                new SensitiveAccessAuditEntity(1L, "t1", "c1", "default", "mobile", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(2L, "t2", "c2", "other", "email", "reason", FIXED_TIME));

        List<SensitiveAccessAuditEntity> result = repository.findByTraceOrSkill(null, "default");

        assertEquals(1, result.size());
        assertEquals("default", result.get(0).skillName());
    }

    @Test
    void findByTraceOrSkillReturnsAllWhenBothNull() {
        InMemorySensitiveAccessAuditRepository repository = new InMemorySensitiveAccessAuditRepository();
        repository.save(
                new SensitiveAccessAuditEntity(1L, "t1", "c1", "default", "mobile", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(2L, "t2", "c2", "other", "email", "reason", FIXED_TIME));

        List<SensitiveAccessAuditEntity> result = repository.findByTraceOrSkill(null, null);

        assertEquals(2, result.size());
    }

    @Test
    void findByTraceIdsOrSkillFiltersByTraceIdSet() {
        InMemorySensitiveAccessAuditRepository repository = new InMemorySensitiveAccessAuditRepository();
        repository.save(
                new SensitiveAccessAuditEntity(1L, "t1", "c1", "default", "mobile", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(2L, "t2", "c2", "default", "email", "reason", FIXED_TIME));
        repository.save(
                new SensitiveAccessAuditEntity(3L, "t3", "c3", "default", "phone", "reason", FIXED_TIME));

        List<SensitiveAccessAuditEntity> result = repository.findByTraceIdsOrSkill(List.of("t1", "t3"), null);

        assertEquals(2, result.size());
    }
}

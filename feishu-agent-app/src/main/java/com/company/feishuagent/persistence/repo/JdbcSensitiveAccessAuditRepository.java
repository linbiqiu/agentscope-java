package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.SensitiveAccessAuditEntity;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSensitiveAccessAuditRepository implements SensitiveAccessAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSensitiveAccessAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(SensitiveAccessAuditEntity entity) {
        jdbcTemplate.update(
                """
                insert into sensitive_access_audit(trace_id, conversation_id, skill_name, field_name, access_reason, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                entity.traceId(),
                entity.conversationId(),
                entity.skillName(),
                entity.fieldName(),
                entity.accessReason(),
                Timestamp.from(entity.createdAt().toInstant()));
    }

    @Override
    public List<SensitiveAccessAuditEntity> findByTraceOrSkill(String traceId, String skillName) {
        if (traceId == null && skillName == null) {
            return jdbcTemplate.query(
                    """
                    select id, trace_id, conversation_id, skill_name, field_name, access_reason, created_at
                    from sensitive_access_audit
                    order by id
                    """,
                    (rs, rowNum) -> toEntity(rs));
        }
        if (traceId != null && skillName != null) {
            return jdbcTemplate.query(
                    """
                    select id, trace_id, conversation_id, skill_name, field_name, access_reason, created_at
                    from sensitive_access_audit
                    where trace_id = ? and skill_name = ?
                    order by id
                    """,
                    (rs, rowNum) -> toEntity(rs),
                    traceId,
                    skillName);
        }
        if (traceId != null) {
            return jdbcTemplate.query(
                    """
                    select id, trace_id, conversation_id, skill_name, field_name, access_reason, created_at
                    from sensitive_access_audit
                    where trace_id = ?
                    order by id
                    """,
                    (rs, rowNum) -> toEntity(rs),
                    traceId);
        }
        return jdbcTemplate.query(
                """
                select id, trace_id, conversation_id, skill_name, field_name, access_reason, created_at
                from sensitive_access_audit
                where skill_name = ?
                order by id
                """,
                (rs, rowNum) -> toEntity(rs),
                skillName);
    }

    @Override
    public List<SensitiveAccessAuditEntity> findByTraceIdsOrSkill(List<String> traceIds, String skillName) {
        if (traceIds == null || traceIds.isEmpty()) {
            return findByTraceOrSkill(null, skillName);
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(traceIds.size(), "?"));
        if (skillName == null) {
            return jdbcTemplate.query(
                    """
                    select id, trace_id, conversation_id, skill_name, field_name, access_reason, created_at
                    from sensitive_access_audit
                    where trace_id in (%s)
                    order by id
                    """.formatted(placeholders),
                    (rs, rowNum) -> toEntity(rs),
                    traceIds.toArray());
        }
        Object[] args = new Object[traceIds.size() + 1];
        for (int i = 0; i < traceIds.size(); i++) {
            args[i] = traceIds.get(i);
        }
        args[traceIds.size()] = skillName;
        return jdbcTemplate.query(
                """
                select id, trace_id, conversation_id, skill_name, field_name, access_reason, created_at
                from sensitive_access_audit
                where trace_id in (%s) and skill_name = ?
                order by id
                """.formatted(placeholders),
                (rs, rowNum) -> toEntity(rs),
                args);
    }

    private SensitiveAccessAuditEntity toEntity(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SensitiveAccessAuditEntity(
                rs.getLong("id"),
                rs.getString("trace_id"),
                rs.getString("conversation_id"),
                rs.getString("skill_name"),
                rs.getString("field_name"),
                rs.getString("access_reason"),
                rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC));
    }
}

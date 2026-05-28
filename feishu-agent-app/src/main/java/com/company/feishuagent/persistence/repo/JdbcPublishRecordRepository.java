package com.company.feishuagent.persistence.repo;

import com.company.feishuagent.persistence.entity.PublishRecordEntity;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublishRecordRepository implements PublishRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPublishRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(PublishRecordEntity entity) {
        jdbcTemplate.update(
                """
                insert into publish_record(target_type, target_id, operator_id, trace_id, change_summary_json, created_at)
                values (?, ?, ?, ?, ?::jsonb, ?)
                """,
                entity.targetType(),
                entity.targetId(),
                entity.operatorId(),
                entity.traceId(),
                entity.changeSummaryJson(),
                Timestamp.from(entity.createdAt().toInstant()));
    }

    @Override
    public List<PublishRecordEntity> findAll() {
        return jdbcTemplate.query(
                """
                select id, target_type, target_id, operator_id, trace_id, change_summary_json::text, created_at
                from publish_record
                order by id
                """,
                (rs, rowNum) ->
                        new PublishRecordEntity(
                                rs.getLong("id"),
                                rs.getString("target_type"),
                                rs.getLong("target_id"),
                                rs.getString("operator_id"),
                                rs.getString("trace_id"),
                                rs.getString("change_summary_json"),
                                rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC)));
    }
}

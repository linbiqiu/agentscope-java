package com.company.feishuagent.persistence.repo;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.event-dedup.backend", havingValue = "jdbc", matchIfMissing = true)
public class JdbcFeishuEventDedupRepository implements FeishuEventDedupRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFeishuEventDedupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean markIfFirst(String eventId, OffsetDateTime createdAt) {
        int updated =
                jdbcTemplate.update(
                        """
                        insert into feishu_event_dedup(event_id, created_at)
                        values (?, ?)
                        on conflict (event_id) do nothing
                        """,
                        eventId,
                        Timestamp.from(createdAt.toInstant()));
        return updated > 0;
    }
}

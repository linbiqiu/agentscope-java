package com.company.feishuagent.persistence.repo;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.rate-limit.backend", havingValue = "jdbc")
public class JdbcFeishuRateLimitRepository implements FeishuRateLimitRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFeishuRateLimitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int incrementAndGet(String userOpenId, long minuteBucket) {
        jdbcTemplate.update(
                """
                insert into feishu_user_rate_limit(user_open_id, minute_bucket, request_count, updated_at)
                values (?, ?, 1, ?)
                on conflict (user_open_id, minute_bucket)
                do update set request_count = feishu_user_rate_limit.request_count + 1, updated_at = excluded.updated_at
                """,
                userOpenId,
                minuteBucket,
                Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select request_count from feishu_user_rate_limit
                        where user_open_id = ? and minute_bucket = ?
                        """,
                        Integer.class,
                        userOpenId,
                        minuteBucket);
        return count == null ? 0 : count;
    }
}

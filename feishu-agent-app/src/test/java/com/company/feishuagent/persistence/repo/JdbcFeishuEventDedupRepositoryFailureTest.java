package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcFeishuEventDedupRepositoryFailureTest {

    @Test
    void markIfFirstPropagatesDataAccessFailure() {
        JdbcFeishuEventDedupRepository repository =
                new JdbcFeishuEventDedupRepository(new FailingJdbcTemplate());

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> repository.markIfFirst("evt_fail", OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private static final class FailingJdbcTemplate extends JdbcTemplate {

        @Override
        public int update(String sql, Object... args) {
            throw new DataAccessResourceFailureException("db unavailable");
        }
    }
}

package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcFeishuRateLimitRepositoryFailureTest {

    @Test
    void incrementAndGetPropagatesDataAccessFailure() {
        JdbcFeishuRateLimitRepository repository =
                new JdbcFeishuRateLimitRepository(new FailingJdbcTemplate());

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> repository.incrementAndGet("u_fail", 100L));
    }

    private static final class FailingJdbcTemplate extends JdbcTemplate {

        @Override
        public int update(String sql, Object... args) {
            throw new DataAccessResourceFailureException("db unavailable");
        }
    }
}

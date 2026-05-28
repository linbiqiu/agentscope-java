package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcFeishuRateLimitRepositoryTest {

    @Test
    void incrementAndGetReturnsQueryCount() {
        JdbcFeishuRateLimitRepository repository =
                new JdbcFeishuRateLimitRepository(new FakeJdbcTemplate(1, 2));

        int result = repository.incrementAndGet("u_1", 100L);

        assertEquals(2, result);
    }

    @Test
    void incrementAndGetReturnsZeroWhenQueryReturnsNull() {
        JdbcFeishuRateLimitRepository repository =
                new JdbcFeishuRateLimitRepository(new FakeJdbcTemplate(1, null));

        int result = repository.incrementAndGet("u_2", 101L);

        assertEquals(0, result);
    }

    private static final class FakeJdbcTemplate extends JdbcTemplate {

        private final int updateResult;
        private final Integer queryResult;

        private FakeJdbcTemplate(int updateResult, Integer queryResult) {
            this.updateResult = updateResult;
            this.queryResult = queryResult;
        }

        @Override
        public int update(String sql, Object... args) {
            return updateResult;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Integer.class) {
                return requiredType.cast(queryResult);
            }
            return null;
        }
    }
}

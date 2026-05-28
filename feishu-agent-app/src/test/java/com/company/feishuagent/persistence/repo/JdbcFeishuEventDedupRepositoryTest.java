package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcFeishuEventDedupRepositoryTest {

    @Test
    void markIfFirstReturnsTrueWhenInsertSucceeds() {
        JdbcFeishuEventDedupRepository repository =
                new JdbcFeishuEventDedupRepository(new FakeJdbcTemplate(1, null));

        boolean result = repository.markIfFirst("evt_1", OffsetDateTime.now(ZoneOffset.UTC));

        assertTrue(result);
    }

    @Test
    void markIfFirstReturnsFalseWhenConflictOccurs() {
        JdbcFeishuEventDedupRepository repository =
                new JdbcFeishuEventDedupRepository(new FakeJdbcTemplate(0, null));

        boolean result = repository.markIfFirst("evt_1", OffsetDateTime.now(ZoneOffset.UTC));

        assertFalse(result);
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

package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryFeishuEventDedupRepositoryTest {

    @Test
    void markIfFirstReturnsFalseOnDuplicateEventId() {
        InMemoryFeishuEventDedupRepository repository = new InMemoryFeishuEventDedupRepository();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        boolean first = repository.markIfFirst("evt_1", now);
        boolean second = repository.markIfFirst("evt_1", now.plusSeconds(1));

        assertTrue(first);
        assertFalse(second);
    }
}

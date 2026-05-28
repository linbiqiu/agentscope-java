package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.company.feishuagent.persistence.entity.PublishRecordEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryPublishRecordRepositoryTest {

    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-22T17:00:00Z");

    @Test
    void saveStoresEntity() {
        InMemoryPublishRecordRepository repository = new InMemoryPublishRecordRepository();
        PublishRecordEntity entity =
                new PublishRecordEntity(
                        1L,
                        "agent_binding",
                        42L,
                        "ops_1",
                        "trace_1",
                        "{\"event\":\"publish_bindings\"}",
                        FIXED_TIME);

        repository.save(entity);
        List<PublishRecordEntity> allItems = repository.findAll();

        assertEquals(1, allItems.size());
        assertEquals("trace_1", allItems.get(0).traceId());
    }
}

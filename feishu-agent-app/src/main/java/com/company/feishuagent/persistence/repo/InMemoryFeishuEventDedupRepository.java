package com.company.feishuagent.persistence.repo;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.event-dedup.backend", havingValue = "memory")
public class InMemoryFeishuEventDedupRepository implements FeishuEventDedupRepository {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markIfFirst(String eventId, OffsetDateTime createdAt) {
        return processedEventIds.add(eventId);
    }
}

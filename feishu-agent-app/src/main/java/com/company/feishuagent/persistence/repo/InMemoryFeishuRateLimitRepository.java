package com.company.feishuagent.persistence.repo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.rate-limit.backend", havingValue = "memory")
public class InMemoryFeishuRateLimitRepository implements FeishuRateLimitRepository {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public int incrementAndGet(String userOpenId, long minuteBucket) {
        String key = userOpenId + "#" + minuteBucket;
        AtomicInteger counter = counters.computeIfAbsent(key, ignored -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }
}

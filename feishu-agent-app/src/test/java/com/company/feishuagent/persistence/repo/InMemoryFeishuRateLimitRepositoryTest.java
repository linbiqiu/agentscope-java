package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InMemoryFeishuRateLimitRepositoryTest {

    @Test
    void incrementAndGetAccumulatesCountByUserAndMinuteBucket() {
        InMemoryFeishuRateLimitRepository repository = new InMemoryFeishuRateLimitRepository();

        int first = repository.incrementAndGet("u_1", 100L);
        int second = repository.incrementAndGet("u_1", 100L);
        int thirdDifferentBucket = repository.incrementAndGet("u_1", 101L);

        assertEquals(1, first);
        assertEquals(2, second);
        assertEquals(1, thirdDifferentBucket);
    }
}

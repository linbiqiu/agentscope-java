package com.company.feishuagent.persistence.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFeishuRateLimitRepositoryTest {

    @Test
    void incrementAndGetIncrementsCounter() {
        RedisFeishuRateLimitRepository repository =
                new RedisFeishuRateLimitRepository(new FakeStringRedisTemplate());

        int first = repository.incrementAndGet("u_1", 100L);
        int second = repository.incrementAndGet("u_1", 100L);

        assertEquals(1, first);
        assertEquals(2, second);
    }

    private static final class FakeStringRedisTemplate extends StringRedisTemplate {

        private final Map<String, Long> counters = new ConcurrentHashMap<>();
        private final ValueOperations<String, String> valueOps = new FakeValueOperations(counters);

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOps;
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public Boolean expire(String key, Duration timeout) {
            return true;
        }
    }

    private static final class FakeValueOperations implements ValueOperations<String, String> {

        private final Map<String, Long> counters;

        private FakeValueOperations(Map<String, Long> counters) {
            this.counters = counters;
        }

        @Override
        public Long increment(String key) {
            return counters.merge(key, 1L, Long::sum);
        }

        @Override
        public void set(String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(String key, String value, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean setIfAbsent(String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean setIfPresent(String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean setIfPresent(String key, String value, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void multiSet(Map<? extends String, ? extends String> map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean multiSetIfAbsent(Map<? extends String, ? extends String> map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String get(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAndDelete(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAndExpire(String key, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAndExpire(String key, Duration timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAndPersist(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAndSet(String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<String> multiGet(java.util.Collection<String> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long increment(String key, long delta) {
            return counters.merge(key, delta, Long::sum);
        }

        @Override
        public Double increment(String key, double delta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long decrement(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long decrement(String key, long delta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Integer append(String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String get(String key, long start, long end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(String key, String value, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long size(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean setBit(String key, long offset, boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean getBit(String key, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Long> bitField(String key, org.springframework.data.redis.connection.BitFieldSubCommands subCommands) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.springframework.data.redis.core.RedisOperations<String, String> getOperations() {
            throw new UnsupportedOperationException();
        }
    }
}

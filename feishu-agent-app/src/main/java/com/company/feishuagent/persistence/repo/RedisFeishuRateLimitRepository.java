package com.company.feishuagent.persistence.repo;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.rate-limit.backend", havingValue = "redis")
public class RedisFeishuRateLimitRepository implements FeishuRateLimitRepository {

    private static final Duration BUCKET_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;

    public RedisFeishuRateLimitRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int incrementAndGet(String userOpenId, long minuteBucket) {
        String key = "feishu:rate-limit:" + userOpenId + ":" + minuteBucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, BUCKET_TTL);
        }
        return count == null ? 0 : count.intValue();
    }
}

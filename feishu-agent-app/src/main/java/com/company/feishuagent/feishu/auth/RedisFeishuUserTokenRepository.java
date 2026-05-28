package com.company.feishuagent.feishu.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.auth.token-backend", havingValue = "redis", matchIfMissing = false)
public class RedisFeishuUserTokenRepository implements FeishuUserTokenRepository {

    private static final Logger logger = LoggerFactory.getLogger(RedisFeishuUserTokenRepository.class);
    private static final String KEY_PREFIX = "feishu:uat:";
    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFeishuUserTokenRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String appId, String userOpenId, FeishuUserToken token) {
        String key = KEY_PREFIX + appId + ":" + userOpenId;
        try {
            String json = objectMapper.writeValueAsString(token);
            redisTemplate.opsForValue().set(key, json, TOKEN_TTL);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize user token for openId={}", userOpenId, e);
        }
    }

    @Override
    public Optional<FeishuUserToken> findByAppIdAndUserOpenId(String appId, String userOpenId) {
        String key = KEY_PREFIX + appId + ":" + userOpenId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, FeishuUserToken.class));
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize user token for openId={}", userOpenId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String appId, String userOpenId) {
        String key = KEY_PREFIX + appId + ":" + userOpenId;
        redisTemplate.delete(key);
    }
}

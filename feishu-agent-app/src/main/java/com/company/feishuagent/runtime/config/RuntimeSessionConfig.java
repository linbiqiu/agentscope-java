package com.company.feishuagent.runtime.config;

import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.RedisSession;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeSessionConfig {

    private static final Duration DEFAULT_SESSION_TTL = Duration.ofDays(1);

    @Bean(destroyMethod = "close")
    public Session runtimeSession(
            @Value("${feishu.session.redis.enabled:true}") boolean redisEnabled,
                @Value("${feishu.session.redis.url:}") String redisUrl,
                @Value("${feishu.session.redis.key-prefix:feishu:session:}") String keyPrefix,
                @Value("${feishu.session.redis.ttl-seconds:86400}") long ttlSeconds,
                @Value("${feishu.session.redis.required:false}") boolean redisRequired) {
            if (!redisEnabled || redisUrl == null || redisUrl.isBlank()) {
                if (redisRequired) {
                    throw new IllegalStateException("Redis session is required but redis is disabled or feishu.session.redis.url is blank");
                }
                return new JsonSession();
            }
        RedisClient redisClient = RedisClient.create(redisUrl);
        Session redisSession = RedisSession.builder().lettuceClient(redisClient).keyPrefix(keyPrefix).build();
        Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : DEFAULT_SESSION_TTL;
        return new RedisSessionTtlWrapper(redisSession, redisClient, keyPrefix, ttl);
    }
}

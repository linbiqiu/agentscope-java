package com.company.feishuagent.runtime.config;

import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.State;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class RedisSessionTtlWrapper implements Session {

    private final Session delegate;
    private final RedisClient redisClient;
    private final String keyPrefix;
    private final Duration ttl;

    RedisSessionTtlWrapper(Session delegate, RedisClient redisClient, String keyPrefix, Duration ttl) {
        this.delegate = delegate;
        this.redisClient = redisClient;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        delegate.save(sessionKey, key, value);
        refreshSessionTtl(sessionKey);
    }

    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        delegate.save(sessionKey, key, values);
        refreshSessionTtl(sessionKey);
    }

    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        return delegate.get(sessionKey, key, type);
    }

    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        return delegate.getList(sessionKey, key, itemType);
    }

    @Override
    public boolean exists(SessionKey sessionKey) {
        return delegate.exists(sessionKey);
    }

    @Override
    public void delete(SessionKey sessionKey) {
        delegate.delete(sessionKey);
    }

    @Override
    public void delete(SessionKey sessionKey, String key) {
        delegate.delete(sessionKey, key);
    }

    @Override
    public Set<SessionKey> listSessionKeys() {
        return delegate.listSessionKeys();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void refreshSessionTtl(SessionKey sessionKey) {
        String sessionId = sessionKey.toIdentifier();
        String keysKey = keyPrefix + sessionId + ":_keys";
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            Set<String> keys = commands.smembers(keysKey);
            commands.expire(keysKey, ttl);
            for (String stateKey : keys) {
                String stateRedisKey = keyPrefix + sessionId + ":" + stateKey;
                String listRedisKey = stateRedisKey + ":list";
                String hashRedisKey = listRedisKey + ":_hash";
                commands.expire(stateRedisKey, ttl);
                commands.expire(listRedisKey, ttl);
                commands.expire(hashRedisKey, ttl);
            }
        }
    }
}

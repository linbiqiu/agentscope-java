package com.company.feishuagent.feishu.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "feishu.auth.token-backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryFeishuUserTokenRepository implements FeishuUserTokenRepository {

    private final Map<String, FeishuUserToken> store = new ConcurrentHashMap<>();

    private static String key(String appId, String userOpenId) {
        return appId + ":" + userOpenId;
    }

    @Override
    public void save(String appId, String userOpenId, FeishuUserToken token) {
        store.put(key(appId, userOpenId), token);
    }

    @Override
    public Optional<FeishuUserToken> findByAppIdAndUserOpenId(String appId, String userOpenId) {
        return Optional.ofNullable(store.get(key(appId, userOpenId)));
    }

    @Override
    public void delete(String appId, String userOpenId) {
        store.remove(key(appId, userOpenId));
    }
}

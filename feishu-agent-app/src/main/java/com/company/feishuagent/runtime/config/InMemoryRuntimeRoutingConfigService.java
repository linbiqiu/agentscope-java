package com.company.feishuagent.runtime.config;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "feishu.runtime.routing.backend", havingValue = "memory")
public class InMemoryRuntimeRoutingConfigService implements RuntimeRoutingConfigService {

    private final ConcurrentHashMap<Long, RuntimeRoutingConfig> storage = new ConcurrentHashMap<>();

    @Override
    public RuntimeRoutingConfig get(Long agentId) {
        return storage.getOrDefault(
                agentId,
                new RuntimeRoutingConfig(
                        agentId,
                        "anthropic",
                        "claude-sonnet-4-6",
                        "",
                        "",
                        "",
                        "HYBRID",
                        "default",
                        0.2,
                        2048,
                        "system"));
    }

    @Override
    public RuntimeRoutingConfig save(RuntimeRoutingConfig config) {
        storage.put(config.agentId(), config);
        return config;
    }
}

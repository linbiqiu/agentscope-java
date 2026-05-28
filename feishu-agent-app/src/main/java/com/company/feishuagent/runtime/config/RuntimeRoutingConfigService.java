package com.company.feishuagent.runtime.config;

public interface RuntimeRoutingConfigService {

    RuntimeRoutingConfig get(Long agentId);

    RuntimeRoutingConfig save(RuntimeRoutingConfig config);
}

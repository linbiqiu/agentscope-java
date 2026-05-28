package com.company.feishuagent.runtime.service;

import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;

public interface RuntimeAgentFactory {

    RuntimeAgentHolder create(RuntimeRoutingConfig config, String requestedSkill);
}

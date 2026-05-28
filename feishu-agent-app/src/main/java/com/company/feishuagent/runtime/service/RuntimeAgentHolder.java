package com.company.feishuagent.runtime.service;

import io.agentscope.core.ReActAgent;

public record RuntimeAgentHolder(ReActAgent delegate, String activatedSkill) {}

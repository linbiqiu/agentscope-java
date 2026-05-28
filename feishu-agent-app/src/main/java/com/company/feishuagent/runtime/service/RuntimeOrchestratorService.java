package com.company.feishuagent.runtime.service;

import com.company.feishuagent.runtime.api.SendMessageRequest;
import com.company.feishuagent.runtime.api.SendMessageResponse;

public interface RuntimeOrchestratorService {

    SendMessageResponse handleMessage(Long agentId, SendMessageRequest request);
}

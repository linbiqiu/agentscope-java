package com.company.feishuagent.runtime.controller;

import com.company.feishuagent.runtime.api.SendMessageRequest;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.service.RuntimeOrchestratorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {

    private final RuntimeOrchestratorService runtimeOrchestratorService;

    public RuntimeController(RuntimeOrchestratorService runtimeOrchestratorService) {
        this.runtimeOrchestratorService = runtimeOrchestratorService;
    }

    @PostMapping("/sessions/{agentId}/messages")
    public SendMessageResponse sendMessage(
            @PathVariable("agentId") Long agentId,
            @Valid @RequestBody SendMessageRequest request) {
        return runtimeOrchestratorService.handleMessage(agentId, request);
    }
}

package com.company.feishuagent.runtime.controller;

import com.company.feishuagent.runtime.api.SaveRuntimeRoutingConfigRequest;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/runtime")
public class RuntimeRoutingConfigController {

    private final RuntimeRoutingConfigService runtimeRoutingConfigService;

    public RuntimeRoutingConfigController(RuntimeRoutingConfigService runtimeRoutingConfigService) {
        this.runtimeRoutingConfigService = runtimeRoutingConfigService;
    }

    @GetMapping("/agents/{agentId}/routing")
    public RuntimeRoutingConfig get(@PathVariable("agentId") Long agentId) {
        return runtimeRoutingConfigService.get(agentId).sanitized();
    }

    @PutMapping("/agents/{agentId}/routing")
    public RuntimeRoutingConfig save(
            @PathVariable("agentId") Long agentId,
            @Valid @RequestBody SaveRuntimeRoutingConfigRequest request) {
        return runtimeRoutingConfigService.save(
                new RuntimeRoutingConfig(
                        agentId,
                        request.provider(),
                        request.model(),
                        request.apiKey() == null ? "" : request.apiKey(),
                        request.baseUrl() == null ? "" : request.baseUrl(),
                        request.fallbackModel() == null ? "" : request.fallbackModel(),
                        request.dispatchMode(),
                        request.manualSkill() == null ? "" : request.manualSkill(),
                        request.temperature(),
                        request.maxTokens(),
                        request.operatorId()));
    }
}

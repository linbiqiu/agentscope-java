package com.company.feishuagent.runtime.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.company.feishuagent.runtime.api.SaveRuntimeRoutingConfigRequest;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeRoutingConfigControllerTest {

    @Mock private RuntimeRoutingConfigService runtimeRoutingConfigService;

    @InjectMocks private RuntimeRoutingConfigController controller;

    @Test
    void getReturnsServiceDataWithoutApiKey() {
        RuntimeRoutingConfig config =
                new RuntimeRoutingConfig(1L, "anthropic", "claude-sonnet-4-6", "sk-secret", "", "", "HYBRID", "default", 0.2, 2048, "ops_1");
        when(runtimeRoutingConfigService.get(1L)).thenReturn(config);

        RuntimeRoutingConfig result = controller.get(1L);

        assertEquals("anthropic", result.provider());
        assertEquals("HYBRID", result.dispatchMode());
        assertEquals("", result.apiKey());
    }

    @Test
    void savePersistsConfig() {
        SaveRuntimeRoutingConfigRequest request =
                new SaveRuntimeRoutingConfigRequest(
                        "ops_1", "openai", "gpt-4o-mini", "sk-test", "https://api.openai.com/v1", "", "MANUAL", "knowledge_search", 0.3, 1024);
        RuntimeRoutingConfig saved =
                new RuntimeRoutingConfig(1L, "openai", "gpt-4o-mini", "sk-test", "https://api.openai.com/v1", "", "MANUAL", "knowledge_search", 0.3, 1024, "ops_1");
        when(runtimeRoutingConfigService.save(saved)).thenReturn(saved);

        RuntimeRoutingConfig result = controller.save(1L, request);

        assertEquals("openai", result.provider());
        assertEquals("MANUAL", result.dispatchMode());
        assertEquals("knowledge_search", result.manualSkill());
    }
}

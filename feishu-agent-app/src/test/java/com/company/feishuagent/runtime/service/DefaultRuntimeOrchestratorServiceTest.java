package com.company.feishuagent.runtime.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.isNull;

import com.company.feishuagent.persistence.repo.InMemoryRuntimeUsageRepository;
import com.company.feishuagent.runtime.api.SendMessageRequest;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.auth.SkillAuthorizationService;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfigService;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlan;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlanResolver;
import com.company.feishuagent.runtime.skill.SkillCatalogService;
import com.company.feishuagent.runtime.skill.SkillMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultRuntimeOrchestratorServiceTest {

    @Mock private SkillAuthorizationService skillAuthorizationService;
    @Mock private RuntimeRoutingConfigService runtimeRoutingConfigService;
    @Mock private RuntimeModelService runtimeModelService;

    @Test
    void handleMessagePassesThroughWhenNoAuthorizedSkills() {
        DefaultRuntimeOrchestratorService service = service();
        SendMessageRequest request = new SendMessageRequest("u_1", "c_1", "p2p", "hello", "analytics", "HYBRID", null);
        when(runtimeRoutingConfigService.get(1L))
                .thenReturn(new RuntimeRoutingConfig(1L, "anthropic", "claude-sonnet-4-6", "", "", "", "HYBRID", "default", 0.2, 2048, "ops"));
        when(skillAuthorizationService.listAllowedSkills(1L, "u_1")).thenReturn(List.of());
        when(runtimeModelService.invoke(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("u_1"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("hello"),
                        org.mockito.ArgumentMatchers.any(RuntimeSkillPlan.class),
                        org.mockito.ArgumentMatchers.isNull(), isNull()))
                .thenReturn(new RuntimeModelResult("model_reply", RuntimeTokenUsage.empty()));

        SendMessageResponse response = service.handleMessage(1L, request);

        assertTrue(response.success());
        assertTrue(response.reply().contains("model_reply"));
    }

    @Test
    void handleMessageAcceptsWhenRequestedSkillAuthorized() {
        DefaultRuntimeOrchestratorService service = service();
        SendMessageRequest request = new SendMessageRequest("u_1", "c_1", "p2p", "hello", "default", "HYBRID", null);
        when(runtimeRoutingConfigService.get(1L))
                .thenReturn(new RuntimeRoutingConfig(1L, "anthropic", "claude-sonnet-4-6", "", "", "", "HYBRID", "default", 0.2, 2048, "ops"));
        when(skillAuthorizationService.listAllowedSkills(1L, "u_1"))
                .thenReturn(List.of("default", "knowledge_search"));
        when(runtimeModelService.invoke(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("u_1"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("hello"),
                        org.mockito.ArgumentMatchers.any(RuntimeSkillPlan.class),
                        org.mockito.ArgumentMatchers.isNull(), isNull()))
                .thenReturn(new RuntimeModelResult("model_reply", RuntimeTokenUsage.empty()));

        SendMessageResponse response = service.handleMessage(1L, request);

        assertTrue(response.success());
        assertTrue(response.reply().contains("model_reply"));

        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(runtimeModelService)
                .invoke(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("u_1"),
                        sessionCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq("hello"),
                        org.mockito.ArgumentMatchers.any(RuntimeSkillPlan.class),
                        org.mockito.ArgumentMatchers.isNull(),
                        isNull());
        assertTrue(sessionCaptor.getValue().contains(":p2p:user:u_1"));
    }

    @Test
    void handleMessageUsesManualSkillWhenDispatchModeManual() {
        DefaultRuntimeOrchestratorService service = service();
        SendMessageRequest request = new SendMessageRequest("u_1", "c_1", "p2p", "hello", "", "MANUAL", null);
        when(runtimeRoutingConfigService.get(1L))
                .thenReturn(new RuntimeRoutingConfig(1L, "anthropic", "claude-sonnet-4-6", "", "", "", "MANUAL", "knowledge_search", 0.2, 2048, "ops"));
        when(skillAuthorizationService.listAllowedSkills(1L, "u_1"))
                .thenReturn(List.of("default", "knowledge_search"));
        when(runtimeModelService.invoke(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("u_1"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("hello"),
                        org.mockito.ArgumentMatchers.any(RuntimeSkillPlan.class),
                        org.mockito.ArgumentMatchers.isNull(), isNull()))
                .thenReturn(new RuntimeModelResult("manual_reply", RuntimeTokenUsage.empty()));

        SendMessageResponse response = service.handleMessage(1L, request);

        assertTrue(response.success());
        assertTrue(response.reply().contains("manual_reply"));
        assertTrue(response.toolCalls().stream().anyMatch(item -> "knowledge_search".equals(item.skillName())));
    }

    @Test
    void handleMessageUsesRequestedSkillWhenHybridAndProvided() {
        DefaultRuntimeOrchestratorService service = service();
        SendMessageRequest request = new SendMessageRequest("u_1", "c_1", "p2p", "hello", "knowledge_search", "HYBRID", null);
        when(runtimeRoutingConfigService.get(1L))
                .thenReturn(new RuntimeRoutingConfig(1L, "anthropic", "claude-sonnet-4-6", "", "", "", "HYBRID", "default", 0.2, 2048, "ops"));
        when(skillAuthorizationService.listAllowedSkills(1L, "u_1"))
                .thenReturn(List.of("default", "knowledge_search"));
        when(runtimeModelService.invoke(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("u_1"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("hello"),
                        org.mockito.ArgumentMatchers.any(RuntimeSkillPlan.class),
                        org.mockito.ArgumentMatchers.isNull(), isNull()))
                .thenReturn(new RuntimeModelResult("hybrid_reply", RuntimeTokenUsage.empty()));

        SendMessageResponse response = service.handleMessage(1L, request);

        assertTrue(response.success());
        assertTrue(response.toolCalls().stream().anyMatch(item -> "knowledge_search".equals(item.skillName())));
    }

    private DefaultRuntimeOrchestratorService service() {
        return new DefaultRuntimeOrchestratorService(
                skillAuthorizationService,
                runtimeRoutingConfigService,
                runtimeModelService,
                skillPlanResolver(),
                new InMemoryRuntimeUsageRepository(),
                new ObjectMapper());
    }

    private RuntimeSkillPlanResolver skillPlanResolver() {
        return new RuntimeSkillPlanResolver(new SkillCatalogService() {
            @Override
            public boolean contains(String skillName) {
                return List.of("default", "knowledge_search", "analytics").contains(skillName);
            }

            @Override
            public List<String> listAvailableSkillNames() {
                return List.of("default", "knowledge_search", "analytics");
            }

            @Override
            public List<SkillMetadata> listSkillMetadata() {
                return List.of(
                        new SkillMetadata("default", "Default", false),
                        new SkillMetadata("knowledge_search", "Knowledge search", false),
                        new SkillMetadata("analytics", "Analytics", false));
            }
        });
    }
}

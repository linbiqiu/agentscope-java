package com.company.feishuagent.feishu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.company.feishuagent.feishu.api.FeishuEventResponse;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.api.ToolCallSummary;
import com.company.feishuagent.runtime.service.RuntimeOrchestratorService;
import com.company.feishuagent.runtime.service.RuntimeTokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeishuEventServiceTest {

    @Mock private RuntimeOrchestratorService runtimeOrchestratorService;

    @Test
    void handleEventReturnsChallengeForUrlVerification() {
        FeishuEventService feishuEventService =
                new FeishuEventService(runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventRequest request = new FeishuEventRequest("url_verification", "abc", "t", null);

        FeishuEventResponse response = feishuEventService.handleEvent(request);

        assertTrue(response.success());
        assertEquals("abc", response.challenge());
    }

    @Test
    void handleEventRejectsInvalidToken() {
        FeishuEventService feishuEventService =
                new FeishuEventService(
                        runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "expected");
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_1", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, "wrong", event);

        FeishuEventResponse response = feishuEventService.handleEvent(request);

        assertFalse(response.success());
        assertEquals("invalid token", response.reply());
    }

    @Test
    void handleEventRejectsInvalidPayload() {
        FeishuEventService feishuEventService =
                new FeishuEventService(runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, null);

        FeishuEventResponse response = feishuEventService.handleEvent(request);

        assertFalse(response.success());
        assertEquals("invalid event payload", response.reply());
    }

    @Test
    void handleEventRejectsWhenRateLimitExceeded() {
        FeishuEventService feishuEventService =
                new FeishuEventService(
                        runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "", 1);
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_rate_1", "text", "p2p", "u_rate", "c_1", "hello", "agent_main", "default", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, event);
        SendMessageResponse runtimeResponse =
                new SendMessageResponse(
                        true,
                        "trace_rate",
                        "ok",
                        List.of(new ToolCallSummary("default", "none", "success", 0)),
                        RuntimeTokenUsage.empty());
        when(runtimeOrchestratorService.handleMessage(any(), any())).thenReturn(runtimeResponse);

        FeishuEventResponse first = feishuEventService.handleEvent(request);
        FeishuEventResponse second = feishuEventService.handleEvent(request);

        assertTrue(first.success());
        assertFalse(second.success());
        assertEquals("rate limit exceeded", second.reply());
        verify(runtimeOrchestratorService, times(1)).handleMessage(any(), any());
    }

    @Test
    void handleEventReturnsAvailableSkillsWhenRequestedSkillUnauthorized() {
        RuntimeOrchestratorService runtimeOrchestratorService =
                (agentId, request) ->
                        new SendMessageResponse(
                                false,
                                "trace_unauth",
                                "Requested skill is not authorized. Available skills: default",
                                List.of(), RuntimeTokenUsage.empty());
        FeishuEventService feishuEventService =
                new FeishuEventService(runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_unauth",
                        "text",
                        "p2p",
                        "u_1",
                        "c_1",
                        "hello",
                        "agent_main",
                        "analytics", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, event);

        FeishuEventResponse response = feishuEventService.handleEvent(request);

        assertFalse(response.success());
        assertTrue(response.reply().contains("Available skills: default"));
    }

    @Test
    void handleEventRetriesRuntimeFailureAndReturnsSuccess() {
        FeishuEventService feishuEventService =
                new FeishuEventService(
                        runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "", 0, 1);
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_retry_ok", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, event);
        SendMessageResponse runtimeResponse =
                new SendMessageResponse(
                        true,
                        "trace_retry_ok",
                        "ok",
                        List.of(new ToolCallSummary("default", "none", "success", 0)),
                        RuntimeTokenUsage.empty());
        when(runtimeOrchestratorService.handleMessage(any(), any()))
                .thenThrow(new RuntimeException("temporary"))
                .thenReturn(runtimeResponse);

        FeishuEventResponse response = feishuEventService.handleEvent(request);

        assertTrue(response.success());
        assertEquals("trace_retry_ok", response.traceId());
        verify(runtimeOrchestratorService, times(2)).handleMessage(any(), any());
    }

    @Test
    void handleEventThrowsAfterRetryExhausted() {
        FeishuEventService feishuEventService =
                new FeishuEventService(
                        runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "", 0, 1);
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_retry_fail", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, event);
        when(runtimeOrchestratorService.handleMessage(any(), any()))
                .thenThrow(new RuntimeException("still failing"));

        assertThrows(RuntimeException.class, () -> feishuEventService.handleEvent(request));
        verify(runtimeOrchestratorService, times(2)).handleMessage(any(), any());
    }

    @Test
    void handleEventIgnoresDuplicateEvent() {
        FeishuEventService feishuEventService =
                new FeishuEventService(runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_dup", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, event);
        SendMessageResponse runtimeResponse =
                new SendMessageResponse(
                        true,
                        "trace_1",
                        "ok",
                        List.of(new ToolCallSummary("default", "none", "success", 0)),
                        RuntimeTokenUsage.empty());
        when(runtimeOrchestratorService.handleMessage(any(), any())).thenReturn(runtimeResponse);

        FeishuEventResponse first = feishuEventService.handleEvent(request);
        FeishuEventResponse second = feishuEventService.handleEvent(request);

        assertTrue(first.success());
        assertEquals("trace_1", first.traceId());
        assertTrue(second.success());
        assertEquals(null, second.reply());
        verify(runtimeOrchestratorService).handleMessage(any(), any());
    }

    @Test
    void handleEventBridgesToRuntime() {
        FeishuEventService feishuEventService =
                new FeishuEventService(runtimeOrchestratorService, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        "evt_2", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null);
        FeishuEventRequest request = new FeishuEventRequest("event_callback", null, null, event);
        SendMessageResponse runtimeResponse =
                new SendMessageResponse(
                        true,
                        "trace_2",
                        "ok",
                        List.of(new ToolCallSummary("default", "none", "success", 0)),
                        RuntimeTokenUsage.empty());
        when(runtimeOrchestratorService.handleMessage(any(), any())).thenReturn(runtimeResponse);

        FeishuEventResponse response = feishuEventService.handleEvent(request);

        assertTrue(response.success());
        assertEquals("trace_2", response.traceId());
        assertEquals("ok", response.reply());
        verify(runtimeOrchestratorService).handleMessage(any(), any());
    }
}

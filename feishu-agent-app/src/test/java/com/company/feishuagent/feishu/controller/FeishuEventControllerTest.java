package com.company.feishuagent.feishu.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.company.feishuagent.feishu.api.FeishuEventResponse;
import com.company.feishuagent.feishu.service.FeishuEventIdempotencyService;
import com.company.feishuagent.feishu.service.FeishuEventService;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.api.ToolCallSummary;
import com.company.feishuagent.runtime.service.RuntimeOrchestratorService;
import com.company.feishuagent.runtime.service.RuntimeOrchestratorService;
import com.company.feishuagent.runtime.service.RuntimeTokenUsage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class FeishuEventControllerTest {

    @Test
    void eventsReturnsServiceResponse() {
        RuntimeOrchestratorService runtime =
                (agentId, request) ->
                        new SendMessageResponse(
                                true,
                                "trace_controller",
                                "ok",
                                List.of(new ToolCallSummary("default", "none", "success", 0)),
                                RuntimeTokenUsage.empty());
        FeishuEventService service =
                new FeishuEventService(runtime, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventController controller = new FeishuEventController(service, "", 300);
        FeishuEventRequest request =
                new FeishuEventRequest(
                        "event_callback",
                        null,
                        null,
                        new FeishuEventRequest.FeishuEvent(
                                "evt_1", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null));

        FeishuEventResponse response = controller.events(request, null, null);

        assertEquals(true, response.success());
        assertEquals("trace_controller", response.traceId());
        assertEquals("ok", response.reply());
    }

    @Test
    void eventsRejectsInvalidSignatureWhenSecretConfigured() {
        RuntimeOrchestratorService runtime =
                (agentId, request) ->
                        new SendMessageResponse(
                                true,
                                "trace_controller",
                                "ok",
                                List.of(new ToolCallSummary("default", "none", "success", 0)),
                                RuntimeTokenUsage.empty());
        FeishuEventService service =
                new FeishuEventService(runtime, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventController controller = new FeishuEventController(service, "secret", 300);
        FeishuEventRequest request =
                new FeishuEventRequest(
                        "event_callback",
                        null,
                        null,
                        new FeishuEventRequest.FeishuEvent(
                                "evt_1", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null));
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        FeishuEventResponse response = controller.events(request, timestamp, "invalid");

        assertFalse(response.success());
        assertEquals("invalid signature", response.reply());
    }

    @Test
    void eventsAcceptsValidSignatureWhenSecretConfigured() {
        RuntimeOrchestratorService runtime =
                (agentId, request) ->
                        new SendMessageResponse(
                                true,
                                "trace_controller",
                                "ok",
                                List.of(new ToolCallSummary("default", "none", "success", 0)),
                                RuntimeTokenUsage.empty());
        FeishuEventService service =
                new FeishuEventService(runtime, new FeishuEventIdempotencyService(new com.company.feishuagent.persistence.repo.InMemoryFeishuEventDedupRepository()), "");
        FeishuEventController controller = new FeishuEventController(service, "secret", 300);
        FeishuEventRequest request =
                new FeishuEventRequest(
                        "event_callback",
                        null,
                        "token_x",
                        new FeishuEventRequest.FeishuEvent(
                                "evt_1", "text", "p2p", "u_1", "c_1", "hello", "agent_main", "default", null, null, null, null));
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = hmacSha256Hex("secret", timestamp + request.type() + request.token());

        FeishuEventResponse response = controller.events(request, timestamp, signature);

        assertEquals(true, response.success());
        assertEquals("trace_controller", response.traceId());
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(signed.length * 2);
            for (byte item : signed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

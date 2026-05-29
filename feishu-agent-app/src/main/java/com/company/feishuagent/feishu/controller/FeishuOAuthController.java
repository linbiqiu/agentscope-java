package com.company.feishuagent.feishu.controller;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.feishu.auth.FeishuUserToken;
import com.company.feishuagent.feishu.auth.FeishuUserTokenService;
import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.feishu.service.FeishuReplyService;
import com.company.feishuagent.runtime.api.SendMessageRequest;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.service.RuntimeOrchestratorService;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feishu/oauth")
public class FeishuOAuthController {

    private static final Logger logger = LoggerFactory.getLogger(FeishuOAuthController.class);

    private final FeishuUserTokenService tokenService;
    private final FeishuApiClient feishuApiClient;
    private final RuntimeOrchestratorService runtimeOrchestratorService;
    private final FeishuReplyService feishuReplyService;
    private final Client openApiClient;

    public FeishuOAuthController(
            FeishuUserTokenService tokenService,
            FeishuApiClient feishuApiClient,
            RuntimeOrchestratorService runtimeOrchestratorService,
            FeishuReplyService feishuReplyService,
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret) {
        this.tokenService = tokenService;
        this.feishuApiClient = feishuApiClient;
        this.runtimeOrchestratorService = runtimeOrchestratorService;
        this.feishuReplyService = feishuReplyService;
        this.openApiClient = Client.newBuilder(appId, appSecret).build();
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("redirectUri") String redirectUri) {
        String state = String.valueOf(System.currentTimeMillis());
        String url = tokenService.buildOAuthUrl(redirectUri, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {
        try {
            FeishuUserToken token = tokenService.exchangeCodeForToken(code);
            String openId = token.userOpenId();
            logger.info("OAuth callback success: openId={}", openId);

            Map<String, String> pending = feishuApiClient.getAndClearOAuthPendingRequest(openId);
            boolean hasPending = pending != null && pending.get("message") != null;

            if (hasPending) {
                logger.info("OAuth pending request found for openId={}, will auto-execute async: {}", openId, pending.get("message"));
                Map<String, String> pendingCopy = pending;
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                    replyToOrigin(pendingCopy, "授权成功！正在为您执行之前的请求...");
                    executePendingRequest(pendingCopy);
                }).start();
            } else {
                notifyUser(openId, "授权成功！您现在可以使用日历、消息等飞书功能了。请发送您的请求。");
            }

            String html = buildSuccessHtml(hasPending);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (Exception e) {
            logger.error("OAuth callback failed: {}", e.getMessage());
            String html = buildErrorHtml(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        }
    }

    private void executePendingRequest(Map<String, String> pending) {
        try {
            String openId = pending.get("openId");
            String chatId = pending.get("chatId");
            String chatType = pending.get("chatType");
            String message = pending.get("message");
            String identityContextJson = pending.get("identityContextJson");
            EnterpriseIdentity identity = deserializeIdentity(pending.get("identityJson"));

            SendMessageRequest request = new SendMessageRequest(
                    openId,
                    chatId,
                    chatType,
                    message,
                    null,
                    null,
                    identityContextJson,
                    identity);

            SendMessageResponse response = runtimeOrchestratorService.handleMessage(1L, request);

            if (response.reply() != null && !response.reply().isBlank()) {
                replyToOrigin(pending, response.reply());
            }
            logger.info("OAuth auto-execution completed for openId={} success={}", openId, response.success());
        } catch (Exception e) {
            logger.error("OAuth auto-execution failed for openId={}: {}", pending.get("openId"), e.getMessage());
            replyToOrigin(pending, "授权成功但自动执行失败，请重新发送您的请求。");
        }
    }

    private void replyToOrigin(Map<String, String> pending, String text) {
        String messageId = pending != null ? pending.get("messageId") : null;
        String chatType = pending != null ? pending.get("chatType") : null;
        if (messageId != null && !messageId.isBlank()) {
            feishuReplyService.replyText(messageId, text, chatType);
        } else {
            String openId = pending != null ? pending.get("openId") : null;
            if (openId != null) {
                notifyUser(openId, text);
            }
        }
    }

    private EnterpriseIdentity deserializeIdentity(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(json, EnterpriseIdentity.class);
        } catch (Exception e) {
            logger.warn("Failed to deserialize identity: {}", e.getMessage());
            return null;
        }
    }

    private void notifyUser(String openId, String text) {
        try {
            String content = "{\"text\":\"" + escapeJson(text) + "\"}";
            CreateMessageReqBody body = CreateMessageReqBody.newBuilder()
                    .receiveId(openId)
                    .msgType("text")
                    .content(content)
                    .build();
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id")
                    .createMessageReqBody(body)
                    .build();
            CreateMessageResp resp = openApiClient.im().v1().message().create(req);
            if (resp != null && resp.success()) {
                logger.info("OAuth notification sent to openId={}", openId);
            } else {
                logger.warn("OAuth notification failed: code={} msg={}",
                        resp == null ? "null" : resp.getCode(),
                        resp == null ? "null" : resp.getMsg());
            }
        } catch (Exception e) {
            logger.warn("OAuth notification failed for openId={}: {}", openId, e.getMessage());
        }
    }

    private String buildSuccessHtml(boolean autoExecuting) {
        String tip = autoExecuting
                ? "应用正在后台为您处理之前的请求，请返回飞书查看结果。"
                : "请返回飞书对话，发送您的请求即可。";
        String closeTip = autoExecuting
                ? "本页面将在 3 秒后自动关闭，您也可以直接关闭此页面。"
                : "本页面将在 3 秒后自动关闭。";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<title>授权成功</title>"
                + "<style>body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;"
                + "display:flex;justify-content:center;align-items:center;height:100vh;margin:0;"
                + "background:#f5f5f5}"
                + ".card{background:#fff;border-radius:12px;padding:40px;text-align:center;"
                + "box-shadow:0 2px 8px rgba(0,0,0,0.1);max-width:400px}"
                + ".icon{font-size:48px;margin-bottom:16px}"
                + "h1{color:#1a1a1a;font-size:20px;margin:0 0 8px}"
                + "p{color:#666;font-size:14px;margin:0 0 20px;line-height:1.5}"
                + ".tip{color:#999;font-size:12px}</style></head>"
                + "<body><div class='card'>"
                + "<div class='icon'>&#x2705;</div>"
                + "<h1>授权成功</h1>"
                + "<p>您已成功授权飞书应用。</p>"
                + "<p class='tip'>" + escapeHtml(tip) + "</p>"
                + "<p class='tip'>" + escapeHtml(closeTip) + "</p>"
                + "</div>"
                + "<script>setTimeout(function(){window.close()},3000)</script>"
                + "</body></html>";
    }

    private String buildErrorHtml(String error) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<title>授权失败</title>"
                + "<style>body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;"
                + "display:flex;justify-content:center;align-items:center;height:100vh;margin:0;"
                + "background:#f5f5f5}"
                + ".card{background:#fff;border-radius:12px;padding:40px;text-align:center;"
                + "box-shadow:0 2px 8px rgba(0,0,0,0.1);max-width:400px}"
                + ".icon{font-size:48px;margin-bottom:16px}"
                + "h1{color:#d32f2f;font-size:20px;margin:0 0 8px}"
                + "p{color:#666;font-size:14px;margin:0 0 20px;line-height:1.5}</style></head>"
                + "<body><div class='card'>"
                + "<div class='icon'>&#x274C;</div>"
                + "<h1>授权失败</h1>"
                + "<p>" + escapeHtml(error) + "</p>"
                + "<p>请返回飞书对话重试，或联系管理员。</p>"
                + "</div></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }
}

package com.company.feishuagent.feishu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BotOpenIdProvider {

    private static final Logger logger = LoggerFactory.getLogger(BotOpenIdProvider.class);

    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appSecret;
    private final String configuredOpenId;
    private final AtomicReference<String> cachedOpenId = new AtomicReference<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public BotOpenIdProvider(
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret,
            @Value("${feishu.bot.open-id:}") String configuredOpenId,
            ObjectMapper objectMapper) {
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.configuredOpenId = configuredOpenId == null ? "" : configuredOpenId.trim();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!configuredOpenId.isBlank()) {
            cachedOpenId.set(configuredOpenId);
            logger.info("bot_open_id source=config value={}", configuredOpenId);
            return;
        }
        fetchAndCache();
    }

    private void fetchAndCache() {
        try {
            String token = getTenantAccessToken();
            if (token == null) {
                logger.warn("bot_open_id_fetch_skipped reason=no_tenant_token");
                return;
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/bot/v3/info"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                logger.warn("bot_info_api_error code={} msg={}", code, root.path("msg").asText(""));
                return;
            }
            JsonNode botNode = root.path("bot");
            String openId = botNode.path("open_id").asText("");
            if (!openId.isBlank()) {
                cachedOpenId.set(openId);
                logger.info("bot_open_id source=api value={}", openId);
            } else {
                logger.warn("bot_info_no_open_id response={}", root);
            }
        } catch (Exception ex) {
            logger.warn("bot_info_fetch_exception reason={}", ex.getMessage());
        }
    }

    private String getTenantAccessToken() {
        try {
            String body = "{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + appSecret + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                logger.warn("tenant_token_error code={}", code);
                return null;
            }
            return root.path("tenant_access_token").asText(null);
        } catch (Exception ex) {
            logger.warn("tenant_token_exception reason={}", ex.getMessage());
            return null;
        }
    }

    public String getBotOpenId() {
        String val = cachedOpenId.get();
        if (val == null || val.isBlank()) {
            fetchAndCache();
            val = cachedOpenId.get();
        }
        return val;
    }

    public boolean isBotOpenId(String openId) {
        if (openId == null || openId.isBlank()) return false;
        String botId = getBotOpenId();
        return botId != null && !botId.isBlank() && botId.equals(openId);
    }
}

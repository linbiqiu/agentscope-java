package com.company.feishuagent.feishu.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeishuApiClient {

    private static final Logger logger = LoggerFactory.getLogger(FeishuApiClient.class);
    private static final String OAUTH_REQUIRED_PREFIX = "feishu:oauth_required:";
    private static final String OAUTH_PENDING_PREFIX = "feishu:oauth_pending:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private final Client openApiClient;
    private final FeishuUserTokenService tokenService;
    private final String appBaseUrl;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public FeishuApiClient(
            Client openApiClient,
            FeishuUserTokenService tokenService,
            @Value("${feishu.auth.app-base-url:}") String appBaseUrl,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.tokenService = tokenService;
        this.appBaseUrl = appBaseUrl != null ? appBaseUrl.replaceAll("/+$", "") : "";
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Client getClient() {
        return openApiClient;
    }

    public Optional<String> getUserAccessToken(String userOpenId) {
        Optional<FeishuUserToken> token = tokenService.getValidToken(userOpenId);
        if (token.isEmpty()) {
            logger.warn("No valid UAT found for openId={}", userOpenId);
            return Optional.empty();
        }
        return Optional.of(token.get().accessToken());
    }

    public RequestOptions uatOptions(String userOpenId) {
        Optional<String> token = getUserAccessToken(userOpenId);
        RequestOptions options = new RequestOptions();
        token.ifPresent(options::setUserAccessToken);
        return options;
    }

    public boolean hasUat(String userOpenId) {
        return tokenService.getValidToken(userOpenId).isPresent();
    }

    public String buildOAuthUrlForUser(String userOpenId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return "";
        }
        String redirectUri = appBaseUrl + "/feishu/oauth/callback";
        String state = userOpenId;
        return tokenService.buildOAuthUrl(redirectUri, state);
    }

    public void markOAuthRequired(String openId) {
        try {
            redisTemplate.opsForValue().set(OAUTH_REQUIRED_PREFIX + openId, "1", PENDING_TTL);
        } catch (Exception e) {
            logger.warn("markOAuthRequired failed openId={}: {}", openId, e.getMessage());
        }
    }

    public boolean isOAuthRequired(String openId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(OAUTH_REQUIRED_PREFIX + openId));
        } catch (Exception e) {
            return false;
        }
    }

    public void clearOAuthRequired(String openId) {
        try {
            redisTemplate.delete(OAUTH_REQUIRED_PREFIX + openId);
        } catch (Exception e) {
            logger.warn("clearOAuthRequired failed openId={}: {}", openId, e.getMessage());
        }
    }

    public void saveOAuthPendingRequest(String openId, String message, String chatId, String chatType,
                                          String identityJson, String identityContextJson) {
        try {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("openId", openId);
            data.put("message", message);
            data.put("chatId", chatId);
            data.put("chatType", chatType);
            if (identityJson != null && !identityJson.isBlank()) {
                data.put("identityJson", identityJson);
            }
            if (identityContextJson != null && !identityContextJson.isBlank()) {
                data.put("identityContextJson", identityContextJson);
            }
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(OAUTH_PENDING_PREFIX + openId, json, PENDING_TTL);
            logger.info("OAuth pending request saved for openId={}", openId);
        } catch (Exception e) {
            logger.warn("saveOAuthPendingRequest failed openId={}: {}", openId, e.getMessage());
        }
    }

    public Map<String, String> getAndClearOAuthPendingRequest(String openId) {
        try {
            String key = OAUTH_PENDING_PREFIX + openId;
            String json = redisTemplate.opsForValue().getAndDelete(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> data = objectMapper.readValue(json, Map.class);
            logger.info("OAuth pending request retrieved for openId={}", openId);
            return data;
        } catch (Exception e) {
            logger.warn("getOAuthPendingRequest failed openId={}: {}", openId, e.getMessage());
            return null;
        }
    }
}

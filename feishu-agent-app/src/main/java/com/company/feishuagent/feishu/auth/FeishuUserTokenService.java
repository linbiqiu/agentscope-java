package com.company.feishuagent.feishu.auth;

import com.lark.oapi.Client;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReqBody;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenResp;
import com.lark.oapi.service.authen.v1.model.CreateRefreshAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateRefreshAccessTokenReqBody;
import com.lark.oapi.service.authen.v1.model.CreateRefreshAccessTokenResp;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeishuUserTokenService {

    private static final Logger logger = LoggerFactory.getLogger(FeishuUserTokenService.class);
    private static final long REFRESH_MARGIN_MS = 5 * 60 * 1000L;

    private final Client openApiClient;
    private final FeishuUserTokenRepository tokenRepository;
    private final String appId;
    private final String appSecret;

    public FeishuUserTokenService(
            Client openApiClient,
            FeishuUserTokenRepository tokenRepository,
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret) {
        this.openApiClient = openApiClient;
        this.tokenRepository = tokenRepository;
        this.appId = appId;
        this.appSecret = appSecret;
    }

    private static final String OAUTH_SCOPES =
            "calendar:calendar"
            + " im:message"
            + " contact:user.base:readonly"
            + " docx:document"
            + " sheets:spreadsheet"
            + " bitable:app"
            + " offline_access";

    public String buildOAuthUrl(String redirectUri, String state) {
        return "https://open.feishu.cn/open-apis/authen/v1/authorize"
                + "?app_id=" + appId
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8)
                + "&state=" + state
                + "&scope=" + java.net.URLEncoder.encode(OAUTH_SCOPES, java.nio.charset.StandardCharsets.UTF_8);
    }

    public FeishuUserToken exchangeCodeForToken(String code) throws Exception {
        CreateAccessTokenReqBody body = CreateAccessTokenReqBody.newBuilder()
                .grantType("authorization_code")
                .code(code)
                .build();
        CreateAccessTokenReq req =
                CreateAccessTokenReq.newBuilder().createAccessTokenReqBody(body).build();
        CreateAccessTokenResp resp = openApiClient.authen().v1().accessToken().create(req);
        if (!resp.success()) {
            throw new RuntimeException("OAuth code exchange failed: " + resp.getCode() + " " + resp.getMsg());
        }
        CreateAccessTokenResp respTyped = resp;
        var data = respTyped.getData();
        FeishuUserToken token = new FeishuUserToken(
                data.getOpenId(),
                data.getAccessToken(),
                data.getRefreshToken(),
                System.currentTimeMillis() + data.getExpiresIn() * 1000L,
                "",
                data.getTokenType());
        tokenRepository.save(appId, token.userOpenId(), token);
        logger.info("UAT saved for openId={}", token.userOpenId());
        return token;
    }

    public Optional<FeishuUserToken> getValidToken(String userOpenId) {
        Optional<FeishuUserToken> stored = tokenRepository.findByAppIdAndUserOpenId(appId, userOpenId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        FeishuUserToken token = stored.get();
        if (!token.isExpiringSoon(REFRESH_MARGIN_MS)) {
            return Optional.of(token);
        }
        try {
            FeishuUserToken refreshed = refreshToken(token);
            return Optional.of(refreshed);
        } catch (Exception e) {
            logger.error("Token refresh failed for openId={}, error={}", userOpenId, e.getMessage());
            if (!token.isExpired()) {
                return Optional.of(token);
            }
            tokenRepository.delete(appId, userOpenId);
            return Optional.empty();
        }
    }

    private FeishuUserToken refreshToken(FeishuUserToken old) throws Exception {
        CreateRefreshAccessTokenReqBody body = CreateRefreshAccessTokenReqBody.newBuilder()
                .grantType("refresh_token")
                .refreshToken(old.refreshToken())
                .build();
        CreateRefreshAccessTokenReq req =
                CreateRefreshAccessTokenReq.newBuilder().createRefreshAccessTokenReqBody(body).build();
        CreateRefreshAccessTokenResp resp = openApiClient.authen().v1().refreshAccessToken().create(req);
        if (!resp.success()) {
            throw new RuntimeException("Token refresh failed: " + resp.getCode() + " " + resp.getMsg());
        }
        var data = resp.getData();
        FeishuUserToken refreshed = new FeishuUserToken(
                old.userOpenId(),
                data.getAccessToken(),
                data.getRefreshToken(),
                System.currentTimeMillis() + data.getExpiresIn() * 1000L,
                "",
                old.tokenType());
        tokenRepository.save(appId, refreshed.userOpenId(), refreshed);
        logger.info("UAT refreshed for openId={}", refreshed.userOpenId());
        return refreshed;
    }
}

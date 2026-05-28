package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.core.request.RequestOptions;
import io.agentscope.core.agent.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FeishuApiToolHelper {

    private static final Logger logger = LoggerFactory.getLogger(FeishuApiToolHelper.class);

    private FeishuApiToolHelper() {}

    static IdentityResult resolveIdentityAndUat(RuntimeContext ctx, FeishuApiClient feishuApiClient) {
        logger.info("resolveIdentityAndUat called ctx={}", ctx != null ? "present" : "null");
        if (ctx == null) {
            return IdentityResult.fail("identity_not_available", "RuntimeContext is null");
        }
        EnterpriseIdentity identity = resolveIdentity(ctx);
        logger.info("resolveIdentityAndUat identity={}", identity);
        if (identity == null || identity.openId() == null || identity.openId().isBlank()) {
            return IdentityResult.fail("identity_not_available", "Cannot determine user identity");
        }
        String openId = identity.openId();
        boolean hasToken;
        try {
            hasToken = feishuApiClient.hasUat(openId);
        } catch (Exception e) {
            logger.error("hasUat failed openId={} error={}", openId, e.getMessage());
            return IdentityResult.fail("token_check_failed", e.getMessage());
        }
        if (!hasToken) {
            String authorizeUrl = feishuApiClient.buildOAuthUrlForUser(openId);
            feishuApiClient.markOAuthRequired(openId);
            logger.info("oauth_required openId={} authorizeUrl={}", openId, authorizeUrl);
            return IdentityResult.fail(
                    "oauth_required",
                    "User has not authorized the app. Please click the link below to complete authorization.",
                    authorizeUrl);
        }
        RequestOptions options = feishuApiClient.uatOptions(openId);
        return IdentityResult.ok(options);
    }

    static EnterpriseIdentity resolveIdentity(RuntimeContext ctx) {
        EnterpriseIdentity typed = ctx.get(EnterpriseIdentity.class);
        if (typed != null) return typed;
        Object fromKey = ctx.get("enterprise_identity");
        if (fromKey instanceof EnterpriseIdentity id) return id;
        return null;
    }

    static final class IdentityResult {
        private final boolean ok;
        private final RequestOptions options;
        private final String error;
        private final String errorMessage;
        private final String authorizeUrl;

        private IdentityResult(boolean ok, RequestOptions options, String error, String errorMessage, String authorizeUrl) {
            this.ok = ok;
            this.options = options;
            this.error = error;
            this.errorMessage = errorMessage;
            this.authorizeUrl = authorizeUrl;
        }

        static IdentityResult ok(RequestOptions options) {
            return new IdentityResult(true, options, null, null, null);
        }

        static IdentityResult fail(String error, String message, String authorizeUrl) {
            return new IdentityResult(false, null, error, message, authorizeUrl);
        }

        static IdentityResult fail(String error, String message) {
            return fail(error, message, null);
        }

        boolean isOk() {
            return ok;
        }

        RequestOptions options() {
            return options;
        }

        String errorJson() {
            ObjectMapper mapper = new ObjectMapper();
            try {
                java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
                map.put("error", error);
                map.put("message", errorMessage);
                if (authorizeUrl != null && !authorizeUrl.isBlank()) {
                    map.put("authorize_url", authorizeUrl);
                }
                return mapper.writeValueAsString(map);
            } catch (Exception e) {
                return "{\"error\":\"" + error + "\"}";
            }
        }
    }
}

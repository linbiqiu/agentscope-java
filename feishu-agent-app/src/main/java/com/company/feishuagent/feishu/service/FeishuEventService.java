package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.company.feishuagent.feishu.api.FeishuEventResponse;
import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.feishu.model.FeishuMessageContext;
import com.company.feishuagent.persistence.repo.FeishuRateLimitRepository;
import com.company.feishuagent.persistence.repo.InMemoryFeishuRateLimitRepository;
import com.company.feishuagent.runtime.api.SendMessageRequest;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.service.RuntimeOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeishuEventService {

    private static final Logger logger = LoggerFactory.getLogger(FeishuEventService.class);

    private final RuntimeOrchestratorService runtimeOrchestratorService;
    private final FeishuEventIdempotencyService feishuEventIdempotencyService;
    private final FeishuRateLimitRepository feishuRateLimitRepository;
    private final FeishuInboundMessageMapper feishuInboundMessageMapper;
    private final FeishuMessageGate feishuMessageGate;
    private final FeishuIdentityResolver feishuIdentityResolver;
    private final FeishuReplyService feishuReplyService;
    private final FeishuApiClient feishuApiClient;
    private final String verificationToken;
    private final int maxRequestsPerMinutePerUser;
    private final int runtimeMaxRetries;

    @Autowired
    public FeishuEventService(
            RuntimeOrchestratorService runtimeOrchestratorService,
            FeishuEventIdempotencyService feishuEventIdempotencyService,
            FeishuRateLimitRepository feishuRateLimitRepository,
            FeishuInboundMessageMapper feishuInboundMessageMapper,
            FeishuMessageGate feishuMessageGate,
            FeishuIdentityResolver feishuIdentityResolver,
            FeishuReplyService feishuReplyService,
            FeishuApiClient feishuApiClient,
            @Value("${feishu.verification-token:}") String verificationToken,
            @Value("${feishu.rate-limit.per-user-per-minute:0}") int maxRequestsPerMinutePerUser,
            @Value("${feishu.runtime.max-retries:0}") int runtimeMaxRetries) {
        this.runtimeOrchestratorService = runtimeOrchestratorService;
        this.feishuEventIdempotencyService = feishuEventIdempotencyService;
        this.feishuRateLimitRepository = feishuRateLimitRepository;
        this.feishuInboundMessageMapper = feishuInboundMessageMapper;
        this.feishuMessageGate = feishuMessageGate;
        this.feishuIdentityResolver = feishuIdentityResolver;
        this.feishuReplyService = feishuReplyService;
        this.feishuApiClient = feishuApiClient;
        this.verificationToken = verificationToken;
        this.maxRequestsPerMinutePerUser = maxRequestsPerMinutePerUser;
        this.runtimeMaxRetries = runtimeMaxRetries;
    }

    public FeishuEventService(
            RuntimeOrchestratorService runtimeOrchestratorService,
            FeishuEventIdempotencyService feishuEventIdempotencyService,
            String verificationToken) {
        this(runtimeOrchestratorService, feishuEventIdempotencyService, verificationToken, 0, 0);
    }

    FeishuEventService(
            RuntimeOrchestratorService runtimeOrchestratorService,
            FeishuEventIdempotencyService feishuEventIdempotencyService,
            String verificationToken,
            int maxRequestsPerMinutePerUser) {
        this(runtimeOrchestratorService, feishuEventIdempotencyService, verificationToken, maxRequestsPerMinutePerUser, 0);
    }

    FeishuEventService(
            RuntimeOrchestratorService runtimeOrchestratorService,
            FeishuEventIdempotencyService feishuEventIdempotencyService,
            String verificationToken,
            int maxRequestsPerMinutePerUser,
            int runtimeMaxRetries) {
        this(
                runtimeOrchestratorService,
                feishuEventIdempotencyService,
                new InMemoryFeishuRateLimitRepository(),
                new FeishuInboundMessageMapper(new com.fasterxml.jackson.databind.ObjectMapper(), new BotOpenIdProvider("", "", "", new com.fasterxml.jackson.databind.ObjectMapper())),
                new FeishuMessageGate(new BotOpenIdProvider("", "", "", new com.fasterxml.jackson.databind.ObjectMapper()), true, false, true),
                FeishuIdentityResolver.noOp(),
                FeishuReplyService.noOp(),
                null,
                verificationToken,
                maxRequestsPerMinutePerUser,
                runtimeMaxRetries);
    }

    public FeishuEventResponse handleEvent(FeishuEventRequest request) {
        if ("url_verification".equals(request.type())) {
            return new FeishuEventResponse(true, request.challenge(), null, null);
        }
        if (verificationToken != null
                && !verificationToken.isBlank()
                && !verificationToken.equals(request.token())) {
            logger.warn("feishu_event_rejected reason=invalid_token");
            return new FeishuEventResponse(false, null, null, "invalid token");
        }
        if (request.event() == null || request.event().userOpenId() == null || request.event().text() == null) {
            logger.warn("feishu_event_rejected reason=invalid_payload");
            return new FeishuEventResponse(false, null, null, "invalid event payload");
        }
        if (isRateLimited(request.event().userOpenId())) {
            logger.warn("feishu_event_rejected reason=rate_limited userOpenId={}", request.event().userOpenId());
            return new FeishuEventResponse(false, null, null, "rate limit exceeded");
        }
        if (!feishuEventIdempotencyService.markIfFirst(request.event().eventId())) {
            logger.info("feishu_event_skipped reason=duplicate eventId={}", request.event().eventId());
            return new FeishuEventResponse(true, null, null, null);
        }

        FeishuMessageContext messageContext = feishuInboundMessageMapper.fromEventRequest(request);
        GateResult gateResult = feishuMessageGate.check(messageContext);
        if (!gateResult.allowed()) {
            logger.info(
                    "feishu_event_skipped reason={} eventId={} chatType={}",
                    gateResult.reason(),
                    request.event().eventId(),
                    messageContext == null ? null : messageContext.chatType());
            return new FeishuEventResponse(true, null, null, gateResult.userVisibleReply());
        }

        EnterpriseIdentity enterpriseIdentity = resolveIdentity(request, messageContext);
        String processingCardId = feishuReplyService.sendProcessingCard(messageContext);

        Long agentId = resolveAgentId(request.event().agentCode());
        SendMessageRequest runtimeRequest =
                new SendMessageRequest(
                        request.event().userOpenId(),
                        request.event().chatId(),
                        request.event().chatType(),
                        messageContext.content(),
                        request.event().requestedSkill(),
                        null,
                        request.event().identityContextJson(),
                        enterpriseIdentity);

        SendMessageResponse response = invokeRuntimeWithRetry(agentId, runtimeRequest);

        if (processingCardId != null) {
            feishuReplyService.updateCardWithReply(processingCardId, response.reply(), response.success());
        }

        savePendingRequestIfOAuthRequired(
                request.event().userOpenId(),
                messageContext != null ? messageContext.content() : null,
                request.event().chatId(),
                request.event().chatType(),
                request.event().identityContextJson(),
                enterpriseIdentity,
                messageContext != null ? messageContext.messageId() : null);

        return new FeishuEventResponse(response.success(), null, response.traceId(), response.reply());
    }

    private EnterpriseIdentity resolveIdentity(FeishuEventRequest request, FeishuMessageContext messageContext) {
        String openId = messageContext != null ? messageContext.senderOpenId() : null;
        if (openId == null || openId.isBlank()) {
            openId = request.event().userOpenId();
        }
        if (openId == null || openId.isBlank()) {
            return null;
        }
        try {
            return feishuIdentityResolver.resolve(openId);
        } catch (Exception ex) {
            logger.warn("feishu_identity_resolve_error openId={} reason={}", openId, ex.getMessage());
            return null;
        }
    }

    private SendMessageResponse invokeRuntimeWithRetry(Long agentId, SendMessageRequest runtimeRequest) {
        int totalAttempts = Math.max(0, runtimeMaxRetries) + 1;
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return runtimeOrchestratorService.handleMessage(agentId, runtimeRequest);
            } catch (RuntimeException ex) {
                lastException = ex;
                logger.warn(
                        "runtime_call_failed attempt={} totalAttempts={} userId={} reason={}",
                        attempt,
                        totalAttempts,
                        runtimeRequest.userId(),
                        ex.getMessage());
            }
        }
        throw lastException;
    }

    private boolean isRateLimited(String userOpenId) {
        if (maxRequestsPerMinutePerUser <= 0) {
            return false;
        }
        long currentMinute = System.currentTimeMillis() / 60000;
        int currentCount = feishuRateLimitRepository.incrementAndGet(userOpenId, currentMinute);
        return currentCount > maxRequestsPerMinutePerUser;
    }

    private void savePendingRequestIfOAuthRequired(String openId, String message, String chatId, String chatType,
                                                     String identityContextJson, EnterpriseIdentity enterpriseIdentity,
                                                     String messageId) {
        if (feishuApiClient == null || openId == null || message == null) {
            return;
        }
        if (feishuApiClient.isOAuthRequired(openId)) {
            feishuApiClient.clearOAuthRequired(openId);
            String identityJson = serializeIdentity(enterpriseIdentity);
            feishuApiClient.saveOAuthPendingRequest(openId, message, chatId, chatType, identityJson, identityContextJson, messageId);
            logger.info("OAuth pending request saved for openId={} message={}", openId, message);
        }
    }

    private String serializeIdentity(EnterpriseIdentity identity) {
        if (identity == null) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(identity);
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveAgentId(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return 1L;
        }
        return Math.abs((long) agentCode.hashCode());
    }
}

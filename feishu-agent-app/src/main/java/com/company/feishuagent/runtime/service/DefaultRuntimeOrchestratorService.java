package com.company.feishuagent.runtime.service;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.persistence.entity.RuntimeCallUsageHourlyEntity;
import com.company.feishuagent.persistence.entity.RuntimeCallUsageLogEntity;
import com.company.feishuagent.persistence.repo.RuntimeUsageRepository;
import com.company.feishuagent.runtime.api.SendMessageRequest;
import com.company.feishuagent.runtime.api.SendMessageResponse;
import com.company.feishuagent.runtime.api.ToolCallSummary;
import com.company.feishuagent.runtime.auth.SkillAuthorizationService;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfigService;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlan;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlanResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultRuntimeOrchestratorService implements RuntimeOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRuntimeOrchestratorService.class);
    private static final String DEFAULT_SKILL = "default";

    private final SkillAuthorizationService skillAuthorizationService;
    private final RuntimeRoutingConfigService runtimeRoutingConfigService;
    private final RuntimeModelService runtimeModelService;
    private final RuntimeSkillPlanResolver runtimeSkillPlanResolver;
    private final RuntimeUsageRepository runtimeUsageRepository;
    private final ObjectMapper objectMapper;
    private final String defaultProvider;
    private final String defaultModel;
    private final String defaultApiKey;
    private final String defaultBaseUrl;
    private final String defaultDispatchMode;
    private final String defaultManualSkill;
    private final double defaultTemperature;
    private final int defaultMaxTokens;

    public DefaultRuntimeOrchestratorService(
            SkillAuthorizationService skillAuthorizationService,
            RuntimeRoutingConfigService runtimeRoutingConfigService,
            RuntimeModelService runtimeModelService,
            RuntimeSkillPlanResolver runtimeSkillPlanResolver,
            RuntimeUsageRepository runtimeUsageRepository,
            ObjectMapper objectMapper) {
        this(
                skillAuthorizationService,
                runtimeRoutingConfigService,
                runtimeModelService,
                runtimeSkillPlanResolver,
                runtimeUsageRepository,
                objectMapper,
                "openai",
                "gpt-4.1",
                "",
                "",
                "HYBRID",
                DEFAULT_SKILL,
                0.2,
                2048);
    }

    @Autowired
    public DefaultRuntimeOrchestratorService(
            SkillAuthorizationService skillAuthorizationService,
            RuntimeRoutingConfigService runtimeRoutingConfigService,
            RuntimeModelService runtimeModelService,
            RuntimeSkillPlanResolver runtimeSkillPlanResolver,
            RuntimeUsageRepository runtimeUsageRepository,
            ObjectMapper objectMapper,
            @Value("${ai.router.provider:openai}") String defaultProvider,
            @Value("${ai.router.model:gpt-4.1}") String defaultModel,
            @Value("${ai.router.api-key:}") String defaultApiKey,
            @Value("${ai.router.base-url:}") String defaultBaseUrl,
            @Value("${feishu.runtime.routing.default-dispatch-mode:HYBRID}") String defaultDispatchMode,
            @Value("${feishu.runtime.routing.default-manual-skill:default}") String defaultManualSkill,
            @Value("${ai.router.temperature:0.2}") double defaultTemperature,
            @Value("${ai.router.max-tokens:2048}") int defaultMaxTokens) {
        this.skillAuthorizationService = skillAuthorizationService;
        this.runtimeRoutingConfigService = runtimeRoutingConfigService;
        this.runtimeModelService = runtimeModelService;
        this.runtimeSkillPlanResolver = runtimeSkillPlanResolver;
        this.runtimeUsageRepository = runtimeUsageRepository;
        this.objectMapper = objectMapper;
        this.defaultProvider = defaultProvider;
        this.defaultModel = defaultModel;
        this.defaultApiKey = defaultApiKey;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultDispatchMode = defaultDispatchMode;
        this.defaultManualSkill = defaultManualSkill;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
    }

    @Override
    public SendMessageResponse handleMessage(Long agentId, SendMessageRequest request) {
        String traceId = UUID.randomUUID().toString();
        RuntimeRoutingConfig config = runtimeRoutingConfigService.get(agentId);
        RuntimeRoutingConfig effectiveConfig = config == null ? defaultConfig(agentId) : config;
        List<String> allowedSkills = skillAuthorizationService.listAllowedSkills(agentId, request.userId());
        String requestedSkill = resolveSkill(request, effectiveConfig);
        RuntimeSkillPlan skillPlan = runtimeSkillPlanResolver.resolve(requestedSkill, allowedSkills);
        String effectiveSkill = skillPlan.requestedSkill();
        String sessionKey = buildSessionKey(agentId, request);
        logger.info(
                "runtime_skill_resolved traceId={} agentId={} userId={} dispatchMode={} requestedSkill={} resolvedSkill={} candidates={}",
                traceId,
                agentId,
                request.userId(),
                request.dispatchMode(),
                request.requestedSkill(),
                effectiveSkill,
                skillPlan.loadableSkillNames());

        OffsetDateTime calledAt = OffsetDateTime.now(ZoneOffset.UTC);
        RuntimeModelResult modelResult = runtimeModelService.invoke(
                effectiveConfig,
                agentId,
                request.userId(),
                sessionKey,
                request.message(),
                skillPlan,
                request.identityContextJson(),
                request.enterpriseIdentity());
        RuntimeTokenUsage usage = modelResult.usage() == null ? RuntimeTokenUsage.empty() : modelResult.usage();

        persistUsage(traceId, agentId, request, effectiveConfig, effectiveSkill, sessionKey, usage, calledAt);

        ToolCallSummary summary = new ToolCallSummary(effectiveSkill, "none", "success", 0);
        return new SendMessageResponse(true, traceId, modelResult.reply(), List.of(summary), usage);
    }

    private void persistUsage(
            String traceId,
            Long agentId,
            SendMessageRequest request,
            RuntimeRoutingConfig config,
            String resolvedSkill,
            String sessionKey,
            RuntimeTokenUsage usage,
            OffsetDateTime calledAt) {
        IdentityProjection identity = resolveIdentityProjection(request);
        RuntimeCallUsageLogEntity logEntity = new RuntimeCallUsageLogEntity(
                null,
                traceId,
                agentId,
                request.userId(),
                request.conversationId(),
                normalizeChatType(request.chatType()),
                identity.actorOpenId(),
                identity.actorMobile(),
                identity.actorEmployeeNo(),
                identity.actorName(),
                config.provider(),
                config.model(),
                identity.orgKey(),
                sessionKey,
                request.requestedSkill(),
                resolvedSkill,
                "success",
                null,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheCreationInputTokens(),
                usage.cacheReadInputTokens(),
                calledAt,
                OffsetDateTime.now(ZoneOffset.UTC));
        runtimeUsageRepository.saveCallLog(logEntity);

        RuntimeCallUsageHourlyEntity hourlyEntity = new RuntimeCallUsageHourlyEntity(
                calledAt.truncatedTo(ChronoUnit.HOURS),
                agentId,
                config.provider(),
                config.model(),
                identity.orgKey(),
                1,
                1,
                0,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheCreationInputTokens(),
                usage.cacheReadInputTokens(),
                OffsetDateTime.now(ZoneOffset.UTC));
        runtimeUsageRepository.upsertHourly(hourlyEntity);
    }

    private IdentityProjection resolveIdentityProjection(SendMessageRequest request) {
        EnterpriseIdentity enterpriseIdentity = request.enterpriseIdentity();
        if (enterpriseIdentity != null) {
            String orgKey = firstNonBlank(enterpriseIdentity.employeeNo(), enterpriseIdentity.mobile(), enterpriseIdentity.openId());
            return new IdentityProjection(
                    blankToNull(enterpriseIdentity.openId()),
                    blankToNull(enterpriseIdentity.mobile()),
                    blankToNull(enterpriseIdentity.employeeNo()),
                    blankToNull(enterpriseIdentity.name()),
                    blankToNull(orgKey));
        }
        String identityContextJson = request.identityContextJson();
        String fallbackOpenId = request.userId();
        if (identityContextJson == null || identityContextJson.isBlank()) {
            return new IdentityProjection(blankToNull(fallbackOpenId), null, null, null, blankToNull(fallbackOpenId));
        }
        try {
            JsonNode root = objectMapper.readTree(identityContextJson);
            JsonNode actor = root.path("actor");
            String actorOpenId = firstNonBlank(text(actor, "openId"), text(actor, "unionId"), text(actor, "userId"), fallbackOpenId);
            String mobile = text(actor, "mobile");
            String employeeNo = text(actor, "employeeNo");
            String actorName = text(actor, "name");
            String orgKey = firstNonBlank(employeeNo, mobile, actorOpenId);
            return new IdentityProjection(blankToNull(actorOpenId), blankToNull(mobile), blankToNull(employeeNo), blankToNull(actorName), blankToNull(orgKey));
        } catch (Exception ex) {
            logger.warn("runtime_identity_parse_failed reason={}", ex.getMessage());
            return new IdentityProjection(blankToNull(fallbackOpenId), null, null, null, blankToNull(fallbackOpenId));
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String buildSessionKey(Long agentId, SendMessageRequest request) {
        String chatType = normalizeChatType(request.chatType());
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "default"
                : request.conversationId().trim();
        if ("p2p".equals(chatType)) {
            return "agent:" + agentId + ":p2p:user:" + request.userId();
        }
        return "agent:" + agentId + ":group:" + conversationId + ":user:" + request.userId();
    }

    private String normalizeChatType(String chatType) {
        if (chatType == null || chatType.isBlank()) {
            return "p2p";
        }
        return chatType.trim().toLowerCase();
    }

    private RuntimeRoutingConfig defaultConfig(Long agentId) {
        return new RuntimeRoutingConfig(
                agentId,
                blankToDefault(defaultProvider, "openai"),
                blankToDefault(defaultModel, "gpt-4.1"),
                blankToDefault(defaultApiKey, ""),
                blankToDefault(defaultBaseUrl, ""),
                "",
                blankToDefault(defaultDispatchMode, "HYBRID"),
                blankToDefault(defaultManualSkill, DEFAULT_SKILL),
                defaultTemperature,
                defaultMaxTokens,
                "system");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String resolveSkill(SendMessageRequest request, RuntimeRoutingConfig config) {
        String mode = request.dispatchMode() == null || request.dispatchMode().isBlank()
                ? config.dispatchMode()
                : request.dispatchMode();
        if ("MANUAL".equalsIgnoreCase(mode)) {
            return blankToDefault(config.manualSkill(), DEFAULT_SKILL);
        }
        if (request.requestedSkill() != null && !request.requestedSkill().isBlank()) {
            return request.requestedSkill().trim();
        }
        return "";
    }

    private record IdentityProjection(String actorOpenId, String actorMobile, String actorEmployeeNo, String actorName, String orgKey) {}
}

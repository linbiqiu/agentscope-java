package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.company.feishuagent.persistence.entity.FeishuUserProfileEntity;
import com.company.feishuagent.persistence.repo.FeishuUserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.contact.v3.model.GetUserResp;
import com.lark.oapi.service.contact.v3.model.User;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.MentionEvent;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.UserId;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeishuWebSocketEventClient {

    private static final Logger logger = LoggerFactory.getLogger(FeishuWebSocketEventClient.class);

    private final FeishuEventService feishuEventService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String appId;
    private final String appSecret;
    private final Client openApiClient;
    private final FeishuUserProfileRepository feishuUserProfileRepository;

    private final ExecutorService clientExecutor;
    private volatile Future<?> clientFuture;

    public FeishuWebSocketEventClient(
            FeishuEventService feishuEventService,
            ObjectMapper objectMapper,
            FeishuUserProfileRepository feishuUserProfileRepository,
            @Value("${feishu.websocket.enabled:false}") boolean enabled,
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret) {
        this.feishuEventService = feishuEventService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.appId = appId;
        this.appSecret = appSecret;
        this.feishuUserProfileRepository = feishuUserProfileRepository;
        this.openApiClient = Client.newBuilder(appId, appSecret).build();
        this.clientExecutor = Executors.newSingleThreadExecutor();
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            logger.info("feishu_websocket_disabled");
            return;
        }
        if (isBlank(appId) || isBlank(appSecret)) {
            logger.warn("feishu_websocket_skipped reason=missing_config");
            return;
        }

        EventDispatcher dispatcher =
                EventDispatcher.newBuilder(appId, appSecret)
                        .onP2MessageReceiveV1(
                                new ImService.P2MessageReceiveV1Handler() {
                                    @Override
                                    public void handle(P2MessageReceiveV1 event) {
                                        FeishuEventRequest request = toEventRequest(event);
                                        if (request == null) {
                                            logger.warn("feishu_websocket_payload_ignored reason=unrecognized");
                                            return;
                                        }
                                        logger.info(
                                                "feishu_ws_message_received eventId={} messageType={} chatType={} userOpenId={} chatId={}",
                                                request.event().eventId(),
                                                request.event().messageType(),
                                                request.event().chatType(),
                                                request.event().userOpenId(),
                                                request.event().chatId());
                                        feishuEventService.handleEvent(request);
                                    }
                                })
                        .build();

        com.lark.oapi.ws.Client wsClient =
                new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                        .eventHandler(dispatcher)
                        .autoReconnect(true)
                        .build();

        this.clientFuture =
                clientExecutor.submit(
                        () -> {
                            try {
                                logger.info("feishu_websocket_connecting sdk=official");
                                wsClient.start();
                            } catch (RuntimeException ex) {
                                logger.warn("feishu_websocket_client_stopped reason={}", ex.getMessage());
                            }
                        });
    }

    @PreDestroy
    public void stop() {
        Future<?> running = this.clientFuture;
        if (running != null) {
            running.cancel(true);
        }
        clientExecutor.shutdownNow();
    }

    private FeishuEventRequest toEventRequest(P2MessageReceiveV1 payload) {
        if (payload == null || payload.getEvent() == null) {
            return null;
        }
        EventMessage message = payload.getEvent().getMessage();
        if (message == null) {
            return null;
        }
        String eventId = message.getMessageId();
        String chatType = message.getChatType();
        String chatId = message.getChatId();
        String text = extractText(message.getContent());
        String messageType = message.getMessageType();
        String rootId = blankToNull(message.getRootId());

        UserId senderId = payload.getEvent().getSender() == null ? null : payload.getEvent().getSender().getSenderId();
        FeishuEventRequest.UserIdentity actor = resolveUserIdentity(senderId, null);
        String userOpenId = actor == null ? null : blankToNull(actor.openId());
        List<FeishuEventRequest.UserIdentity> mentions = resolveMentionIdentities(message.getMentions(), message.getContent());
        String identityContextJson = buildIdentityContextJson(actor, mentions, chatType, chatId, eventId);

        if (isBlank(eventId) || isBlank(userOpenId) || isBlank(text)) {
            return null;
        }

        FeishuEventRequest.FeishuEvent event =
                new FeishuEventRequest.FeishuEvent(
                        eventId,
                        isBlank(messageType) ? "text" : messageType,
                        isBlank(chatType) ? "p2p" : chatType,
                        userOpenId,
                        chatId,
                        text,
                        appId,
                        null,
                        actor,
                        mentions,
                        identityContextJson,
                        rootId);
        return new FeishuEventRequest("event_callback", null, null, event);
    }

    private List<FeishuEventRequest.UserIdentity> resolveMentionIdentities(MentionEvent[] mentionEvents, String content) {
        Map<String, FeishuEventRequest.UserIdentity> deduped = new LinkedHashMap<>();
        if (mentionEvents != null) {
            for (MentionEvent item : mentionEvents) {
                if (item == null) {
                    continue;
                }
                FeishuEventRequest.UserIdentity identity = resolveUserIdentity(item.getId(), item.getName());
                putMention(deduped, identity);
            }
        }
        for (FeishuEventRequest.UserIdentity identity : resolveMentionIdentitiesFromContent(content)) {
            putMention(deduped, identity);
        }
        return List.copyOf(deduped.values());
    }

    private List<FeishuEventRequest.UserIdentity> resolveMentionIdentitiesFromContent(String content) {
        if (isBlank(content)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode textNode = root.path("text");
            if (!textNode.isTextual()) {
                return List.of();
            }
            String text = textNode.asText();
            if (isBlank(text)) {
                return List.of();
            }
            return parseAtIdentities(text);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<FeishuEventRequest.UserIdentity> parseAtIdentities(String text) {
        List<FeishuEventRequest.UserIdentity> identities = new ArrayList<>();
        int cursor = 0;
        while (cursor >= 0 && cursor < text.length()) {
            int start = text.indexOf("<at ", cursor);
            if (start < 0) {
                break;
            }
            int close = text.indexOf("</at>", start);
            if (close < 0) {
                break;
            }
            int end = close + "</at>".length();
            String tag = text.substring(start, end);
            String openId = extractAtAttribute(tag, "open_id");
            String unionId = extractAtAttribute(tag, "union_id");
            String userId = extractAtAttribute(tag, "user_id");
            String displayName = extractAtDisplayName(tag);
            UserId parsed = UserId.newBuilder().openId(openId).unionId(unionId).userId(userId).build();
            FeishuEventRequest.UserIdentity resolved = resolveUserIdentity(parsed, displayName);
            if (resolved != null) {
                identities.add(resolved);
            }
            cursor = end;
        }
        return List.copyOf(identities);
    }

    private String extractAtAttribute(String tag, String attribute) {
        String marker = attribute + "=\"";
        int start = tag.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = tag.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return blankToNull(tag.substring(valueStart, valueEnd));
    }

    private String extractAtDisplayName(String tag) {
        int start = tag.indexOf('>');
        int end = tag.lastIndexOf("</at>");
        if (start < 0 || end <= start) {
            return null;
        }
        return blankToNull(tag.substring(start + 1, end));
    }

    private void putMention(Map<String, FeishuEventRequest.UserIdentity> deduped, FeishuEventRequest.UserIdentity identity) {
        if (identity == null) {
            return;
        }
        String key = firstNonBlank(identity.openId(), identity.unionId(), identity.userId());
        if (isBlank(key)) {
            return;
        }
        deduped.putIfAbsent(key, identity);
    }

    private FeishuEventRequest.UserIdentity resolveUserIdentity(UserId userId, String fallbackName) {
        if (userId == null) {
            return null;
        }
        String openId = blankToNull(userId.getOpenId());
        String unionId = blankToNull(userId.getUnionId());
        String internalUserId = blankToNull(userId.getUserId());
        String lookupId = firstNonBlank(openId, unionId, internalUserId);
        if (isBlank(lookupId)) {
            return null;
        }

        FeishuUserProfileEntity cachedProfile = findCachedProfile(openId, unionId, internalUserId);
        if (cachedProfile != null) {
            return new FeishuEventRequest.UserIdentity(
                    blankToNull(cachedProfile.actorOpenId()),
                    blankToNull(cachedProfile.actorUnionId()),
                    blankToNull(cachedProfile.actorUserId()),
                    blankToNull(cachedProfile.actorMobile()),
                    blankToNull(cachedProfile.actorEmployeeNo()),
                    blankToNull(firstNonBlank(cachedProfile.actorName(), fallbackName)),
                    blankToNull(cachedProfile.actorEmail()));
        }

        User contactUser = fetchUser(lookupId);
        if (contactUser == null) {
            FeishuEventRequest.UserIdentity fallbackIdentity = new FeishuEventRequest.UserIdentity(
                    openId,
                    unionId,
                    internalUserId,
                    null,
                    null,
                    blankToNull(fallbackName),
                    null);
            saveUserProfile(fallbackIdentity);
            return fallbackIdentity;
        }
        String resolvedName = blankToNull(firstNonBlank(contactUser.getName(), fallbackName));
        String normalizedMobile = normalizeMobile(contactUser.getMobile());
        String resolvedMobile = blankToNull(normalizedMobile);
        String resolvedEmployeeNo = blankToNull(contactUser.getEmployeeNo());
        logger.info(
                "feishu_user_resolved lookupId={} openId={} namePresent={} mobilePresent={} employeeNoPresent={}",
                lookupId,
                blankToNull(firstNonBlank(contactUser.getOpenId(), openId)),
                resolvedName != null,
                resolvedMobile != null,
                resolvedEmployeeNo != null);
        FeishuEventRequest.UserIdentity resolvedIdentity = new FeishuEventRequest.UserIdentity(
                blankToNull(firstNonBlank(contactUser.getOpenId(), openId)),
                blankToNull(firstNonBlank(contactUser.getUnionId(), unionId)),
                blankToNull(firstNonBlank(contactUser.getUserId(), internalUserId)),
                resolvedMobile,
                resolvedEmployeeNo,
                resolvedName,
                blankToNull(contactUser.getEmail()));
        saveUserProfile(resolvedIdentity);
        return resolvedIdentity;
    }


    private FeishuUserProfileEntity findCachedProfile(String openId, String unionId, String userId) {
        if (!isBlank(openId)) {
            FeishuUserProfileEntity byOpenId = feishuUserProfileRepository.findByOpenId(openId);
            if (byOpenId != null) {
                return byOpenId;
            }
        }
        if (!isBlank(unionId)) {
            FeishuUserProfileEntity byUnionId = feishuUserProfileRepository.findByUnionId(unionId);
            if (byUnionId != null) {
                return byUnionId;
            }
        }
        if (!isBlank(userId)) {
            return feishuUserProfileRepository.findByUserId(userId);
        }
        return null;
    }

    private User fetchUser(String lookupId) {
        try {
            User userByOpenId = fetchUserWithType(lookupId, "open_id");
            if (userByOpenId != null) {
                return userByOpenId;
            }
            User userByUserId = fetchUserWithType(lookupId, "user_id");
            if (userByUserId != null) {
                return userByUserId;
            }
            User userByUnionId = fetchUserWithType(lookupId, "union_id");
            if (userByUnionId != null) {
                return userByUnionId;
            }
            return null;
        } catch (Exception ex) {
            logger.warn("feishu_fetch_user_exception userId={} reason={}", lookupId, ex.getMessage());
            return null;
        }
    }

    private User fetchUserWithType(String lookupId, String userIdType) {
        try {
            GetUserReq request = GetUserReq.newBuilder()
                    .userIdType(userIdType)
                    .departmentIdType("open_department_id")
                    .userId(lookupId)
                    .build();
            GetUserResp response = openApiClient.contact().v3().user().get(request);
            if (response != null
                    && response.success()
                    && response.getData() != null
                    && response.getData().getUser() != null) {
                User user = response.getData().getUser();
                logUserResponseFields(lookupId, userIdType, user);
                return user;
            }
            if (response != null && response.success()) {
                logger.info(
                        "feishu_fetch_user_empty userId={} userIdType={} hasData={} hasUser={} requestId={}",
                        lookupId,
                        userIdType,
                        response.getData() != null,
                        response.getData() != null && response.getData().getUser() != null,
                        response.getRequestId());
            } else {
                logger.warn(
                        "feishu_fetch_user_failed userId={} userIdType={} code={} msg={}",
                        lookupId,
                        userIdType,
                        response == null ? "null" : response.getCode(),
                        response == null ? "null" : response.getMsg());
            }
            return null;
        } catch (Exception ex) {
            logger.warn(
                    "feishu_fetch_user_type_exception userId={} userIdType={} reason={}",
                    lookupId,
                    userIdType,
                    ex.getMessage());
            return null;
        }
    }

    private void logUserResponseFields(String lookupId, String userIdType, User user) {
        logger.info(
                "feishu_user_response_fields userId={} userIdType={} openIdPresent={} unionIdPresent={} userIdPresent={} namePresent={} displayNamePresent={} enNamePresent={} nicknamePresent={} mobilePresent={} employeeNoPresent={}"
                        + " tenantKeyPresent={}",
                lookupId,
                userIdType,
                notBlank(user.getOpenId()),
                notBlank(user.getUnionId()),
                notBlank(user.getUserId()),
                notBlank(user.getName()),
                hasNonBlankMethod(user, "getDisplayName"),
                hasNonBlankMethod(user, "getEnName"),
                hasNonBlankMethod(user, "getNickname"),
                notBlank(user.getMobile()),
                notBlank(user.getEmployeeNo()),
                hasNonBlankMethod(user, "getTenantKey"));
    }

    private boolean hasNonBlankMethod(User user, String methodName) {
        try {
            Method method = user.getClass().getMethod(methodName);
            Object value = method.invoke(user);
            if (value instanceof String textValue) {
                return notBlank(textValue);
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private void saveUserProfile(FeishuEventRequest.UserIdentity identity) {
        if (!isCompleteProfile(identity)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        feishuUserProfileRepository.upsert(new FeishuUserProfileEntity(
                identity.openId(),
                blankToNull(identity.unionId()),
                blankToNull(identity.userId()),
                blankToNull(identity.name()),
                blankToNull(identity.mobile()),
                blankToNull(identity.employeeNo()),
                blankToNull(identity.email()),
                now,
                now,
                now));
    }

    private boolean isCompleteProfile(FeishuEventRequest.UserIdentity identity) {
        return identity != null
                && notBlank(identity.openId())
                && notBlank(identity.mobile())
                && notBlank(identity.employeeNo())
                && notBlank(identity.email());
    }

    private String normalizeMobile(String mobile) {
        if (isBlank(mobile)) {
            return null;
        }
        String trimmed = mobile.trim();
        if (trimmed.startsWith("+86") && trimmed.length() > 3) {
            return trimmed.substring(3);
        }
        return trimmed;
    }

    private String extractText(String content) {
        if (isBlank(content)) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            String text = text(parsed, "text");
            return isBlank(text) ? null : text;
        } catch (Exception ex) {
            return null;
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
        return isBlank(text) ? null : text;
    }

    private String normalizeChatType(String chatType) {
        if (chatType == null || chatType.isBlank()) {
            return "p2p";
        }
        return chatType.trim().toLowerCase();
    }

    private String buildIdentityContextJson(
            FeishuEventRequest.UserIdentity actor,
            List<FeishuEventRequest.UserIdentity> mentions,
            String chatType,
            String chatId,
            String messageId) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            root.put("platform", "feishu");
            root.set("actor", objectMapper.valueToTree(actor));
            root.set("mentions", objectMapper.valueToTree(mentions == null ? List.of() : mentions));
            com.fasterxml.jackson.databind.node.ObjectNode conversation = root.putObject("conversation");
            conversation.put("chatType", blankToNull(chatType));
            conversation.put("chatId", blankToNull(chatId));
            conversation.put("messageId", blankToNull(messageId));
            return root.toString();
        } catch (RuntimeException ex) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FeishuWebSocketPayloadMapper {

    private final ObjectMapper objectMapper;

    public FeishuWebSocketPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FeishuEventRequest toEventRequest(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            FeishuEventRequest direct = toDirectEvent(root);
            if (direct != null) {
                return direct;
            }
            return toSchemaV2Event(root);
        } catch (IOException ex) {
            return null;
        }
    }

    private FeishuEventRequest toDirectEvent(JsonNode root) {
        if (root == null || !root.has("event")) {
            return null;
        }
        return objectMapper.convertValue(root, FeishuEventRequest.class);
    }

    private FeishuEventRequest toSchemaV2Event(JsonNode root) {
        if (root == null || !root.has("header") || !root.has("event")) {
            return null;
        }
        JsonNode header = root.path("header");
        JsonNode event = root.path("event");

        String eventId = text(header, "event_id");
        if (isBlank(eventId)) {
            eventId = text(event.path("message"), "message_id");
        }
        String chatType = text(event.path("message"), "chat_type");
        String chatId = text(event.path("message"), "chat_id");
        String messageType = text(event.path("message"), "message_type");
        String text = extractText(event.path("message").path("content"));
        String userOpenId = extractUserOpenId(event.path("sender").path("sender_id"));
        String agentCode = text(header, "app_id");

        if (isBlank(eventId) || isBlank(userOpenId) || isBlank(text)) {
            return null;
        }

        FeishuEventRequest.UserIdentity actor =
                toUserIdentity(event.path("sender").path("sender_id"), null, null, null);
        List<FeishuEventRequest.UserIdentity> mentions = parseMentions(event.path("message").path("mentions"));
        String identityContextJson = buildIdentityContextJson(actor, mentions);

        FeishuEventRequest.FeishuEvent feishuEvent =
                new FeishuEventRequest.FeishuEvent(
                        eventId,
                        isBlank(messageType) ? "text" : messageType,
                        isBlank(chatType) ? "p2p" : chatType,
                        userOpenId,
                        chatId,
                        text,
                        agentCode,
                        null,
                        actor,
                        mentions,
                        identityContextJson,
                        null);

        return new FeishuEventRequest("event_callback", null, text(header, "token"), feishuEvent);
    }

    private String extractText(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        String raw = contentNode.asText();
        if (isBlank(raw)) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            String text = text(parsed, "text");
            return isBlank(text) ? raw : text;
        } catch (IOException ex) {
            return raw;
        }
    }

    private String extractUserOpenId(JsonNode senderId) {
        String openId = text(senderId, "open_id");
        if (!isBlank(openId)) {
            return openId;
        }
        String unionId = text(senderId, "union_id");
        if (!isBlank(unionId)) {
            return unionId;
        }
        return text(senderId, "user_id");
    }

    private List<FeishuEventRequest.UserIdentity> parseMentions(JsonNode mentionsNode) {
        if (mentionsNode == null || mentionsNode.isMissingNode() || !mentionsNode.isArray()) {
            return List.of();
        }
        List<FeishuEventRequest.UserIdentity> mentions = new ArrayList<>();
        for (JsonNode item : mentionsNode) {
            FeishuEventRequest.UserIdentity identity =
                    toUserIdentity(item.path("id"), null, null, text(item, "name"));
            if (identity != null && (notBlank(identity.openId()) || notBlank(identity.userId()) || notBlank(identity.unionId()))) {
                mentions.add(identity);
            }
        }
        return List.copyOf(mentions);
    }

    private FeishuEventRequest.UserIdentity toUserIdentity(
            JsonNode userIdNode,
            String mobile,
            String employeeNo,
            String name) {
        if (userIdNode == null || userIdNode.isMissingNode() || userIdNode.isNull()) {
            return null;
        }
        String openId = text(userIdNode, "open_id");
        String unionId = text(userIdNode, "union_id");
        String userId = text(userIdNode, "user_id");
        if (isBlank(openId) && isBlank(unionId) && isBlank(userId)) {
            return null;
        }
        return new FeishuEventRequest.UserIdentity(openId, unionId, userId, mobile, employeeNo, name, null);
    }

    private String buildIdentityContextJson(
            FeishuEventRequest.UserIdentity actor,
            List<FeishuEventRequest.UserIdentity> mentions) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            root.put("platform", "feishu");
            root.set("actor", objectMapper.valueToTree(actor));
            root.set("mentions", objectMapper.valueToTree(mentions == null ? List.of() : mentions));
            return root.toString();
        } catch (RuntimeException ex) {
            return "{}";
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

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

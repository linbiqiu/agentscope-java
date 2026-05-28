package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.im.v1.model.*;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuImToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuImToolGroup.class);

    private final Client openApiClient;
    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuImToolGroup(Client openApiClient, FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_im_message",
            description = "Feishu IM message operations using USER identity (user_access_token). "
                    + "Actions: send (send message), list (list messages in a chat), get (get message by ID). "
                    + "The user must have completed OAuth authorization first.")
    public String imMessage(
            RuntimeContext ctx,
            @ToolParam(name = "action", description = "Action: send, list, get") String action,
            @ToolParam(name = "receive_id", description = "Chat ID or user open_id to send to", required = false)
                    String receiveId,
            @ToolParam(
                            name = "msg_type",
                            description = "Message type: text, post, interactive. Default: text",
                            required = false)
                    String msgType,
            @ToolParam(name = "content", description = "Message content (JSON string)", required = false)
                    String content,
            @ToolParam(name = "chat_id", description = "Chat ID for list messages", required = false) String chatId,
            @ToolParam(name = "message_id", description = "Message ID for get", required = false) String messageId) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            return identity.errorJson();
        }
        try {
            RequestOptions options = identity.options();
            return switch (action.trim().toLowerCase()) {
                case "send" -> sendMessage(receiveId, msgType, content, options);
                case "list" -> listMessages(chatId, options);
                case "get" -> getMessage(messageId, options);
                default -> errorJson("unknown_action", "Supported: send, list, get");
            };
        } catch (Exception e) {
            logger.error("feishu_im_error action={} error={}", action, e.getMessage());
            return errorJson("api_error", e.getMessage());
        }
    }

    private String sendMessage(String receiveId, String msgType, String content, RequestOptions options)
            throws Exception {
        if (receiveId == null || receiveId.isBlank()) {
            return errorJson("missing_params", "receive_id is required");
        }
        String type = (msgType != null && !msgType.isBlank()) ? msgType : "text";
        String body = (content != null && !content.isBlank()) ? content : "{\"text\":\"\"}";
        CreateMessageReqBody reqBody = CreateMessageReqBody.newBuilder()
                .receiveId(receiveId)
                .msgType(type)
                .content(body)
                .build();
        CreateMessageReq req =
                CreateMessageReq.newBuilder().receiveIdType("open_id").createMessageReqBody(reqBody).build();
        CreateMessageResp resp = openApiClient.im().v1().message().create(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("message_id", resp.getData().getMessageId(), "status", "sent"));
    }

    private String listMessages(String chatId, RequestOptions options) throws Exception {
        if (chatId == null || chatId.isBlank()) {
            return errorJson("missing_params", "chat_id is required");
        }
        ListMessageReq req =
                ListMessageReq.newBuilder().containerIdType("chat").containerId(chatId).build();
        ListMessageResp resp = openApiClient.im().v1().message().list(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", resp.getData().getItems());
        result.put("has_more", resp.getData().getHasMore());
        return toJson(result);
    }

    private String getMessage(String messageId, RequestOptions options) throws Exception {
        if (messageId == null || messageId.isBlank()) {
            return errorJson("missing_params", "message_id is required");
        }
        GetMessageReq req = GetMessageReq.newBuilder().messageId(messageId).build();
        GetMessageResp resp = openApiClient.im().v1().message().get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("message", resp.getData().getItems()));
    }

    private String errorJson(String error, String message) {
        return toJson(Map.of("error", error, "message", message));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}

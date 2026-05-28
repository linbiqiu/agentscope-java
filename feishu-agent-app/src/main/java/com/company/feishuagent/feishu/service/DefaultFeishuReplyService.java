package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.model.FeishuMessageContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReactionReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReactionReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageReactionResp;
import com.lark.oapi.service.im.v1.model.Emoji;
import com.lark.oapi.service.im.v1.model.PatchMessageReq;
import com.lark.oapi.service.im.v1.model.PatchMessageReqBody;
import com.lark.oapi.service.im.v1.model.PatchMessageResp;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultFeishuReplyService implements FeishuReplyService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFeishuReplyService.class);
    private static final int LARK_MD_ELEMENT_MAX_LENGTH = 3500;

    private final Client openApiClient;
    private final ObjectMapper objectMapper;

    public DefaultFeishuReplyService(
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret,
            ObjectMapper objectMapper) {
        this.openApiClient = Client.newBuilder(appId, appSecret).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String sendProcessingCard(FeishuMessageContext context) {
        if (context == null || context.messageId() == null || context.messageId().isBlank()) {
            return null;
        }
        try {
            String cardContent = buildProcessingCard();
            ReplyMessageReqBody body = ReplyMessageReqBody.newBuilder()
                    .msgType("interactive")
                    .content(cardContent)
                    .replyInThread(!"p2p".equalsIgnoreCase(context.chatType()))
                    .build();
            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(context.messageId())
                    .replyMessageReqBody(body)
                    .build();
            ReplyMessageResp resp = openApiClient.im().v1().message().reply(req);
            if (resp != null && resp.success() && resp.getData() != null) {
                String cardMsgId = resp.getData().getMessageId();
                logger.info("feishu_processing_card_sent cardMessageId={}", cardMsgId);
                return cardMsgId;
            }
            logger.warn("feishu_processing_card_failed code={} msg={}",
                    resp == null ? "null" : resp.getCode(),
                    resp == null ? "null" : resp.getMsg());
            return null;
        } catch (Exception ex) {
            logger.warn("feishu_processing_card_failed reason={}", ex.getMessage());
            return null;
        }
    }

    @Override
    public void updateCardWithReply(String cardMessageId, String reply, boolean success) {
        if (cardMessageId == null || cardMessageId.isBlank()) {
            return;
        }
        try {
            String cardContent = buildResultCard(reply, success);
            PatchMessageReqBody body = PatchMessageReqBody.newBuilder()
                    .content(cardContent)
                    .build();
            PatchMessageReq req = PatchMessageReq.newBuilder()
                    .messageId(cardMessageId)
                    .patchMessageReqBody(body)
                    .build();
            PatchMessageResp resp = openApiClient.im().v1().message().patch(req);
            if (resp != null && !resp.success()) {
                logger.warn("feishu_card_update_failed code={} msg={}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception ex) {
            logger.warn("feishu_card_update_failed reason={}", ex.getMessage());
        }
    }

    @Override
    public void replyText(String messageId, String text, String chatType) {
        if (messageId == null || messageId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            String postContent = buildPostContent(text);
            ReplyMessageReqBody body = ReplyMessageReqBody.newBuilder()
                    .msgType("post")
                    .content(postContent)
                    .replyInThread(!"p2p".equalsIgnoreCase(normalizeChatType(chatType)))
                    .build();
            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(messageId)
                    .replyMessageReqBody(body)
                    .build();
            ReplyMessageResp resp = openApiClient.im().v1().message().reply(req);
            if (resp != null && !resp.success()) {
                logger.warn("feishu_reply_text_failed code={} msg={}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception ex) {
            logger.warn("feishu_reply_text_failed reason={}", ex.getMessage());
        }
    }

    @Override
    public void sendReaction(String messageId, String emojiType) {
        if (messageId == null || messageId.isBlank() || emojiType == null || emojiType.isBlank()) {
            return;
        }
        try {
            CreateMessageReactionReqBody body = CreateMessageReactionReqBody.newBuilder()
                    .reactionType(Emoji.newBuilder().emojiType(emojiType).build())
                    .build();
            CreateMessageReactionReq req = CreateMessageReactionReq.newBuilder()
                    .messageId(messageId)
                    .createMessageReactionReqBody(body)
                    .build();
            CreateMessageReactionResp resp = openApiClient.im().v1().messageReaction().create(req);
            if (resp != null && !resp.success()) {
                logger.warn("feishu_reaction_failed code={} msg={}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception ex) {
            logger.warn("feishu_reaction_failed reason={}", ex.getMessage());
        }
    }

    private String buildProcessingCard() {
        return "{\"config\":{\"wide_screen_mode\":true},"
                + "\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"\\u26a1 \\u5904\\u7406\\u4e2d...\"},"
                + "\"template\":\"blue\"},"
                + "\"elements\":[{\"tag\":\"div\",\"text\":{\"tag\":\"plain_text\",\"content\":\"\\u6b63\\u5728\\u5206\\u6790\\u60a8\\u7684\\u8bf7\\u6c42\\uff0c\\u8bf7\\u7a0d\\u5019...\"}}]}";
    }

    String buildResultCard(String reply, boolean success) {
        String safeReply = reply == null ? "" : reply;
        String optimizedReply = LarkMarkdownOptimizer.optimize(safeReply);
        String template = success ? "green" : "red";
        String title = success ? "\u2705 \u56de\u7b54" : "\u274c \u5904\u7406\u5931\u8d25";

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode config = root.putObject("config");
            config.put("wide_screen_mode", true);

            ObjectNode header = root.putObject("header");
            ObjectNode headerTitle = header.putObject("title");
            headerTitle.put("tag", "plain_text");
            headerTitle.put("content", title);
            header.put("template", template);

            ArrayNode elements = root.putArray("elements");
            String[] chunks = splitByLength(optimizedReply, LARK_MD_ELEMENT_MAX_LENGTH);
            for (String chunk : chunks) {
                ObjectNode div = elements.addObject();
                div.put("tag", "div");
                ObjectNode text = div.putObject("text");
                text.put("tag", "lark_md");
                text.put("content", chunk);
            }

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            logger.warn("feishu_card_build_fallback reason={}", e.getMessage());
            String escapedReply = escapeJson(optimizedReply);
            return "{\"config\":{\"wide_screen_mode\":true},"
                    + "\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"" + title + "\"},"
                    + "\"template\":\"" + template + "\"},"
                    + "\"elements\":[{\"tag\":\"div\",\"text\":{\"tag\":\"lark_md\",\"content\":\"" + escapedReply + "\"}}]}";
        }
    }

    private String buildPostContent(String text) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode post = root.putObject("zh_cn");
        post.put("title", "");
        ArrayNode content = post.putArray("content");
        ArrayNode line = content.addArray();

        String[] paragraphs = text.split("\\n");
        for (int i = 0; i < paragraphs.length; i++) {
            String p = paragraphs[i];
            if (!p.isBlank()) {
                ObjectNode textNode = line.addObject();
                textNode.put("tag", "text");
                textNode.put("text", p);
            }
            if (i < paragraphs.length - 1) {
                ObjectNode br = line.addObject();
                br.put("tag", "text");
                br.put("text", "\n");
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private String[] splitByLength(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return new String[]{text};
        }
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline + 1;
                }
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks.toArray(new String[0]);
    }

    private String toTextContent(String text) throws JsonProcessingException {
        return objectMapper.createObjectNode().put("text", text).toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String normalizeChatType(String chatType) {
        if (chatType == null || chatType.isBlank()) {
            return "p2p";
        }
        return chatType.trim().toLowerCase();
    }
}

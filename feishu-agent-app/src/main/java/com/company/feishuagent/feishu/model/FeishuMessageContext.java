package com.company.feishuagent.feishu.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record FeishuMessageContext(
        String accountId,
        String appId,
        String eventId,
        String messageId,
        String chatId,
        String chatType,
        String senderOpenId,
        String senderUserId,
        String senderUnionId,
        String senderName,
        boolean senderIsBot,
        String content,
        String contentType,
        List<FeishuMention> mentions,
        boolean mentionAll,
        List<FeishuResource> resources,
        String rootId,
        String parentId,
        String threadId,
        Instant createTime,
        JsonNode rawMessage,
        JsonNode rawSender) {

    public FeishuMessageContext {
        chatType = chatType == null || chatType.isBlank() ? "p2p" : chatType.trim().toLowerCase();
        contentType = contentType == null || contentType.isBlank() ? "text" : contentType.trim();
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}

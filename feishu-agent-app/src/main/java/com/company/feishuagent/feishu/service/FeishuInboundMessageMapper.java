package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.company.feishuagent.feishu.model.FeishuMention;
import com.company.feishuagent.feishu.model.FeishuMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FeishuInboundMessageMapper {

    private final ObjectMapper objectMapper;
    private final BotOpenIdProvider botOpenIdProvider;

    public FeishuInboundMessageMapper(
            ObjectMapper objectMapper,
            BotOpenIdProvider botOpenIdProvider) {
        this.objectMapper = objectMapper;
        this.botOpenIdProvider = botOpenIdProvider;
    }

    public FeishuMessageContext fromEventRequest(FeishuEventRequest request) {
        if (request == null || request.event() == null) {
            return null;
        }
        FeishuEventRequest.FeishuEvent event = request.event();
        List<FeishuMention> mentions = event.mentions() == null
                ? List.of()
                : event.mentions().stream()
                        .map(this::fromIdentity)
                        .toList();
        boolean mentionAll = mentions.stream().anyMatch(FeishuMention::all);
        JsonNode raw = objectMapper.valueToTree(event);
        return new FeishuMessageContext(
                null,
                event.agentCode(),
                event.eventId(),
                event.eventId(),
                event.chatId(),
                event.chatType(),
                event.userOpenId(),
                event.actor() == null ? null : event.actor().userId(),
                event.actor() == null ? null : event.actor().unionId(),
                event.actor() == null ? null : event.actor().name(),
                false,
                stripMentionKeys(event.text(), mentions),
                event.messageType(),
                mentions,
                mentionAll,
                List.of(),
                event.rootId(),
                null,
                null,
                null,
                raw,
                raw);
    }

    private FeishuMention fromIdentity(FeishuEventRequest.UserIdentity identity) {
        String key = identity.openId() != null && identity.openId().equals("@_all") ? "@_all" : null;
        boolean all = "@_all".equals(identity.openId()) || "@_all".equals(identity.userId());
        boolean bot = botOpenIdProvider.isBotOpenId(identity.openId());
        return new FeishuMention(
                key,
                identity.openId(),
                identity.userId(),
                identity.unionId(),
                identity.name(),
                bot,
                all);
    }

    private String stripMentionKeys(String text, List<FeishuMention> mentions) {
        if (text == null || text.isBlank() || mentions == null || mentions.isEmpty()) {
            return text;
        }
        String result = text;
        for (FeishuMention mention : mentions) {
            if (mention.key() != null && !mention.key().isBlank()) {
                result = result.replace(mention.key(), "").trim();
            }
        }
        return result;
    }
}

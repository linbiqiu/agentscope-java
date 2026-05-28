package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.model.FeishuMention;
import com.company.feishuagent.feishu.model.FeishuMessageContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeishuMessageGate {

    private final BotOpenIdProvider botOpenIdProvider;
    private final boolean groupRequireMention;
    private final boolean respondToMentionAll;
    private final boolean directMessageEnabled;

    public FeishuMessageGate(
            BotOpenIdProvider botOpenIdProvider,
            @Value("${feishu.message.group.require-mention:true}") boolean groupRequireMention,
            @Value("${feishu.message.group.respond-to-mention-all:false}") boolean respondToMentionAll,
            @Value("${feishu.message.dm.enabled:true}") boolean directMessageEnabled) {
        this.botOpenIdProvider = botOpenIdProvider;
        this.groupRequireMention = groupRequireMention;
        this.respondToMentionAll = respondToMentionAll;
        this.directMessageEnabled = directMessageEnabled;
    }

    public GateResult check(FeishuMessageContext context) {
        if (context == null) {
            return GateResult.reject("empty_context");
        }
        if (context.senderIsBot()) {
            return GateResult.reject("sender_is_bot");
        }
        if (context.mentionAll() && !respondToMentionAll) {
            return GateResult.reject("mention_all_disabled");
        }
        if ("group".equalsIgnoreCase(context.chatType())) {
            return checkGroup(context);
        }
        if (!directMessageEnabled) {
            return GateResult.reject("dm_disabled");
        }
        return GateResult.allow();
    }

    private GateResult checkGroup(FeishuMessageContext context) {
        if (!groupRequireMention) {
            return GateResult.allow();
        }
        if (mentionsBot(context)) {
            return GateResult.allow();
        }
        if (context.rootId() != null && !context.rootId().isBlank()) {
            return GateResult.allow();
        }
        return GateResult.reject("missing_bot_mention");
    }

    private boolean mentionsBot(FeishuMessageContext context) {
        for (FeishuMention mention : context.mentions()) {
            if (mention.bot()) {
                return true;
            }
            if (botOpenIdProvider.isBotOpenId(mention.openId())) {
                return true;
            }
        }
        return false;
    }
}

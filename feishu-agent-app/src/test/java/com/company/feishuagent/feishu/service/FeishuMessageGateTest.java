package com.company.feishuagent.feishu.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.feishuagent.feishu.model.FeishuMention;
import com.company.feishuagent.feishu.model.FeishuMessageContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeishuMessageGateTest {

    private static BotOpenIdProvider botProvider(String botOpenId) {
        return new BotOpenIdProvider("", "", botOpenId, new ObjectMapper());
    }

    @Test
    void allowsP2pHumanMessage() {
        FeishuMessageGate gate = new FeishuMessageGate(botProvider("bot_open_id"), true, false, true);

        GateResult result = gate.check(context("p2p", false, false, List.of()));

        assertTrue(result.allowed());
    }

    @Test
    void rejectsBotSender() {
        FeishuMessageGate gate = new FeishuMessageGate(botProvider("bot_open_id"), true, false, true);

        GateResult result = gate.check(context("p2p", true, false, List.of()));

        assertFalse(result.allowed());
    }

    @Test
    void rejectsGroupMessageWithoutBotMentionWhenMentionRequired() {
        FeishuMessageGate gate = new FeishuMessageGate(botProvider("bot_open_id"), true, false, true);

        GateResult result = gate.check(context("group", false, false, List.of()));

        assertFalse(result.allowed());
    }

    @Test
    void allowsGroupMessageWithBotMention() {
        FeishuMessageGate gate = new FeishuMessageGate(botProvider("bot_open_id"), true, false, true);

        GateResult result = gate.check(context(
                "group",
                false,
                false,
                List.of(new FeishuMention("@_user_1", "bot_open_id", null, null, "Bot", true, false))));

        assertTrue(result.allowed());
    }

    @Test
    void rejectsMentionAllByDefault() {
        FeishuMessageGate gate = new FeishuMessageGate(botProvider("bot_open_id"), true, false, true);

        GateResult result = gate.check(context(
                "group",
                false,
                true,
                List.of(new FeishuMention("@_all", null, null, null, "所有人", false, true))));

        assertFalse(result.allowed());
    }

    private FeishuMessageContext context(String chatType, boolean senderIsBot, boolean mentionAll, List<FeishuMention> mentions) {
        return new FeishuMessageContext(
                null,
                "app_1",
                "evt_1",
                "msg_1",
                "chat_1",
                chatType,
                "sender_open_id",
                null,
                null,
                "Alice",
                senderIsBot,
                "hello",
                "text",
                mentions,
                mentionAll,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }
}

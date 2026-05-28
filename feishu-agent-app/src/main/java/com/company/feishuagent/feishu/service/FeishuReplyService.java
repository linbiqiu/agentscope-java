package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.model.FeishuMessageContext;

public interface FeishuReplyService {

    String sendProcessingCard(FeishuMessageContext context);

    void updateCardWithReply(String cardMessageId, String reply, boolean success);

    void replyText(String messageId, String text, String chatType);

    void sendReaction(String messageId, String emojiType);

    static FeishuReplyService noOp() {
        return new FeishuReplyService() {
            @Override
            public String sendProcessingCard(FeishuMessageContext context) {
                return null;
            }

            @Override
            public void updateCardWithReply(String cardMessageId, String reply, boolean success) {}

            @Override
            public void replyText(String messageId, String text, String chatType) {}

            @Override
            public void sendReaction(String messageId, String emojiType) {}
        };
    }
}

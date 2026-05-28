package com.company.feishuagent.feishu.model;

public record FeishuMention(
        String key,
        String openId,
        String userId,
        String unionId,
        String name,
        boolean bot,
        boolean all) {}

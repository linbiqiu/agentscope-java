package com.company.feishuagent.runtime.service;

public record RuntimeModelResult(String reply, RuntimeTokenUsage usage) {

    public static RuntimeModelResult ofReply(String reply) {
        return new RuntimeModelResult(reply, RuntimeTokenUsage.empty());
    }
}

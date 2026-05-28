package com.company.feishuagent.feishu.service;

public record GateResult(boolean allowed, String reason, boolean recordOnly, String userVisibleReply) {

    public static GateResult allow() {
        return new GateResult(true, null, false, null);
    }

    public static GateResult reject(String reason) {
        return new GateResult(false, reason, false, null);
    }
}

package com.company.feishuagent.feishu.auth;

public record FeishuUserToken(
        String userOpenId,
        String accessToken,
        String refreshToken,
        long expiresAtEpochMs,
        String scope,
        String tokenType) {

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtEpochMs;
    }

    public boolean isExpiringSoon(long marginMs) {
        return System.currentTimeMillis() >= (expiresAtEpochMs - marginMs);
    }
}

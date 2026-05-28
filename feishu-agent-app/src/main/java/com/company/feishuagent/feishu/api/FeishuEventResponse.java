package com.company.feishuagent.feishu.api;

public record FeishuEventResponse(boolean success, String challenge, String traceId, String reply) {}

package com.company.feishuagent.runtime.tool;

public record ToolAuditRecord(
        String toolName,
        String userId,
        String sessionId,
        String query,
        boolean success,
        String error) {}

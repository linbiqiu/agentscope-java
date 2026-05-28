package com.company.feishuagent.runtime.api;

public record ToolCallSummary(String skillName, String toolName, String status, int latencyMs) {}

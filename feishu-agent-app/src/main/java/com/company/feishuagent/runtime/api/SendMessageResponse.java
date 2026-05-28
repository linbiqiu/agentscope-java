package com.company.feishuagent.runtime.api;

import com.company.feishuagent.runtime.service.RuntimeTokenUsage;
import java.util.List;

public record SendMessageResponse(
        boolean success,
        String traceId,
        String reply,
        List<ToolCallSummary> toolCalls,
        RuntimeTokenUsage usage) {}

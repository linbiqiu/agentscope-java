package com.company.feishuagent.observability.api;

import java.util.List;

public record AuditRecordListResponse(boolean success, List<AuditRecordView> items) {}

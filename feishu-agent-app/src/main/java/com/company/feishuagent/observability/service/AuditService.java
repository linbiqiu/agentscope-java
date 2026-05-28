package com.company.feishuagent.observability.service;

import com.company.feishuagent.observability.api.AuditRecordView;
import java.util.List;

public interface AuditService {

    List<AuditRecordView> listSensitiveAccess(String traceId, String skillName);

    List<AuditRecordView> listSensitiveAccessByTraceIds(List<String> traceIds, String skillName);
}

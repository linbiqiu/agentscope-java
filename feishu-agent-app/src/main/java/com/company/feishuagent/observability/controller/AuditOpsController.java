package com.company.feishuagent.observability.controller;

import com.company.feishuagent.observability.api.AuditRecordListResponse;
import com.company.feishuagent.observability.service.AuditService;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ops")
public class AuditOpsController {

    private static final int MAX_TRACE_IDS = 50;

    private final AuditService auditService;

    public AuditOpsController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit/tool-access")
    public AuditRecordListResponse toolAccess(
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "traceIds", required = false) String traceIds,
            @RequestParam(value = "skillName", required = false) String skillName) {
        if (traceIds != null && !traceIds.isBlank()) {
            List<String> traceIdList = Arrays.stream(traceIds.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
            if (traceIdList.size() > MAX_TRACE_IDS) {
                throw new IllegalArgumentException("traceIds exceeds max size 50");
            }
            return new AuditRecordListResponse(
                    true, auditService.listSensitiveAccessByTraceIds(traceIdList, skillName));
        }
        if ((traceId == null || traceId.isBlank()) && (skillName == null || skillName.isBlank())) {
            throw new IllegalArgumentException("traceId or skillName is required");
        }
        return new AuditRecordListResponse(true, auditService.listSensitiveAccess(traceId, skillName));
    }
}

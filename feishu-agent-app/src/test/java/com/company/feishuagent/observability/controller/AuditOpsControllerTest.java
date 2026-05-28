package com.company.feishuagent.observability.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.company.feishuagent.observability.api.AuditRecordListResponse;
import com.company.feishuagent.observability.api.AuditRecordView;
import com.company.feishuagent.observability.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditOpsControllerTest {

    @Mock private AuditService auditService;

    @InjectMocks private AuditOpsController controller;

    @Test
    void toolAccessReturnsServiceResultsBySingleTraceId() {
        AuditRecordView item =
                new AuditRecordView(
                        "trace_1",
                        "conv_1",
                        "default",
                        "mobile",
                        "tool required user contact",
                        "2026-05-22T17:00:00Z");
        when(auditService.listSensitiveAccess("trace_1", "default")).thenReturn(List.of(item));

        AuditRecordListResponse result = controller.toolAccess("trace_1", null, "default");

        assertTrue(result.success());
        assertEquals(1, result.items().size());
        assertEquals("trace_1", result.items().get(0).traceId());
    }

    @Test
    void toolAccessReturnsServiceResultsByTraceIds() {
        AuditRecordView item =
                new AuditRecordView(
                        "trace_1",
                        "conv_1",
                        "default",
                        "mobile",
                        "tool required user contact",
                        "2026-05-22T17:00:00Z");
        when(auditService.listSensitiveAccessByTraceIds(List.of("trace_1", "trace_2"), "default"))
                .thenReturn(List.of(item));

        AuditRecordListResponse result = controller.toolAccess(null, "trace_1,trace_2", "default");

        assertTrue(result.success());
        assertEquals(1, result.items().size());
        assertEquals("trace_1", result.items().get(0).traceId());
    }
}

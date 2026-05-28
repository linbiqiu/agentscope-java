package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnterpriseToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(EnterpriseToolGroup.class);

    private final ObjectMapper objectMapper;

    public EnterpriseToolGroup(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "enterprise_get_user_profile",
            description = "Get the current user's enterprise identity profile including name, mobile, "
                    + "employee number, email, and department. Use this to understand who is making the request.")
    public String getUserProfile(RuntimeContext ctx) {
        EnterpriseIdentity identity = resolveIdentity(ctx);
        if (identity == null) {
            return toJson(Map.of(
                    "error", "identity_not_available",
                    "message", "Enterprise identity is not available for this session."));
        }
        auditToolCall(ctx, "enterprise_get_user_profile", "full_profile", identity.openId());
        return toJson(Map.of(
                "openId", nullSafe(identity.openId()),
                "name", nullSafe(identity.name()),
                "mobile", nullSafe(identity.mobile()),
                "employeeNo", nullSafe(identity.employeeNo()),
                "email", nullSafe(identity.email()),
                "departmentId", nullSafe(identity.departmentId()),
                "isComplete", identity.isComplete()));
    }

    @Tool(
            name = "enterprise_check_identity_field",
            description = "Check if the current user has a specific identity field available (e.g. mobile, employeeNo, email). "
                    + "Returns true/false with the field value if present.")
    public String checkIdentityField(
            RuntimeContext ctx,
            @ToolParam(name = "field", description = "The identity field to check: mobile, employeeNo, email, name, departmentId")
            String field) {
        EnterpriseIdentity identity = resolveIdentity(ctx);
        if (identity == null) {
            return toJson(Map.of("available", false, "reason", "identity_not_available"));
        }
        return switch (field.trim().toLowerCase()) {
            case "mobile" -> toJson(Map.of("available", identity.hasMobile(), "value", nullSafe(identity.mobile())));
            case "employeeno" -> toJson(Map.of(
                    "available", identity.hasEmployeeNo(), "value", nullSafe(identity.employeeNo())));
            case "email" -> toJson(Map.of("available", notBlank(identity.email()), "value", nullSafe(identity.email())));
            case "name" -> toJson(Map.of("available", notBlank(identity.name()), "value", nullSafe(identity.name())));
            case "departmentid" -> toJson(Map.of(
                    "available", notBlank(identity.departmentId()), "value", nullSafe(identity.departmentId())));
            default -> toJson(Map.of("available", false, "reason", "unknown_field: " + field));
        };
    }

    private void auditToolCall(RuntimeContext ctx, String toolName, String query, String userOpenId) {
        logger.info(
                "tool_audit toolName={} query={} userOpenId={} sessionId={}",
                toolName,
                query,
                userOpenId,
                ctx == null ? null : ctx.getSessionId());
    }

    private EnterpriseIdentity resolveIdentity(RuntimeContext ctx) {
        if (ctx == null) {
            return null;
        }
        EnterpriseIdentity typed = ctx.get(EnterpriseIdentity.class);
        if (typed != null) {
            return typed;
        }
        Object fromKey = ctx.get("enterprise_identity");
        if (fromKey instanceof EnterpriseIdentity identity) {
            return identity;
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

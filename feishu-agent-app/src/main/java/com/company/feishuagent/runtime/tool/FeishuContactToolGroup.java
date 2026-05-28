package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.contact.v3.model.*;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuContactToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuContactToolGroup.class);

    private final Client openApiClient;
    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuContactToolGroup(Client openApiClient, FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_contact_user",
            description = "Feishu contact/directory lookup using USER identity (user_access_token). "
                    + "Actions: get (get user detail by open_id), list (list users with pagination), "
                    + "search (find users by department). "
                    + "The user must have completed OAuth authorization first.")
    public String contactUser(
            RuntimeContext ctx,
            @ToolParam(name = "action", description = "Action: get, list, search") String action,
            @ToolParam(name = "open_id", description = "User open_id, required for get", required = false)
                    String openId,
            @ToolParam(
                            name = "department_id",
                            description = "Department open_department_id, required for search",
                            required = false)
                    String departmentId,
            @ToolParam(
                            name = "page_size",
                            description = "Page size for list, default 20",
                            required = false)
                    String pageSize) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            return identity.errorJson();
        }
        try {
            RequestOptions options = identity.options();
            return switch (action.trim().toLowerCase()) {
                case "get" -> getUser(openId, options);
                case "list" -> listUsers(pageSize, options);
                case "search" -> searchByDepartment(departmentId, pageSize, options);
                default -> errorJson("unknown_action", "Supported: get, list, search");
            };
        } catch (Exception e) {
            logger.error("feishu_contact_error action={} error={}", action, e.getMessage());
            return errorJson("api_error", e.getMessage());
        }
    }

    private String getUser(String openId, RequestOptions options) throws Exception {
        if (openId == null || openId.isBlank()) {
            return errorJson("missing_params", "open_id is required");
        }
        GetUserReq req = GetUserReq.newBuilder().userId(openId).userIdType("open_id").build();
        GetUserResp resp = openApiClient.contact().v3().user().get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("user", resp.getData().getUser()));
    }

    private String listUsers(String pageSize, RequestOptions options) throws Exception {
        int size = parsePageSize(pageSize);
        ListUserReq req = ListUserReq.newBuilder().userIdType("open_id").pageSize(size).build();
        ListUserResp resp = openApiClient.contact().v3().user().list(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", resp.getData().getItems());
        result.put("has_more", resp.getData().getHasMore());
        return toJson(result);
    }

    private String searchByDepartment(String departmentId, String pageSize, RequestOptions options) throws Exception {
        if (departmentId == null || departmentId.isBlank()) {
            return errorJson("missing_params", "department_id is required");
        }
        int size = parsePageSize(pageSize);
        FindByDepartmentUserReq req = FindByDepartmentUserReq.newBuilder()
                .departmentId(departmentId)
                .departmentIdType("open_department_id")
                .userIdType("open_id")
                .pageSize(size)
                .build();
        FindByDepartmentUserResp resp = openApiClient.contact().v3().user().findByDepartment(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", resp.getData().getItems());
        result.put("has_more", resp.getData().getHasMore());
        return toJson(result);
    }

    private int parsePageSize(String pageSize) {
        if (pageSize == null || pageSize.isBlank()) return 20;
        try {
            return Math.min(Math.max(Integer.parseInt(pageSize), 1), 50);
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    private String errorJson(String error, String message) {
        return toJson(Map.of("error", error, "message", message));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}

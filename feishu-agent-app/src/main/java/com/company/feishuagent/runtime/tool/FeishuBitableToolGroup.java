package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.bitable.v1.model.*;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuBitableToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuBitableToolGroup.class);

    private final Client openApiClient;
    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuBitableToolGroup(Client openApiClient, FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_bitable",
            description = "Feishu Bitable (multi-dimensional table) operations using USER identity (user_access_token). "
                    + "Actions: get_app (get app info), list_tables (list tables in app), "
                    + "list_records (list records in a table), get_record (get single record), "
                    + "create_record (create a new record), list_fields (list table fields). "
                    + "Supports app tokens from URLs like https://xxx.feishu.cn/base/APP_TOKEN. "
                    + "The user must have completed OAuth authorization first.")
    public String bitableOperation(
            RuntimeContext ctx,
            @ToolParam(name = "action", description = "Action: get_app, list_tables, list_records, get_record, create_record, list_fields")
                    String action,
            @ToolParam(name = "app_token", description = "Bitable app token (from URL path after /base/)", required = false)
                    String appToken,
            @ToolParam(name = "table_id", description = "Table ID within the bitable app", required = false)
                    String tableId,
            @ToolParam(name = "record_id", description = "Record ID for get_record", required = false)
                    String recordId,
            @ToolParam(name = "fields_json", description = "JSON object of field values for create_record, e.g. {\"field1\":\"value1\"}", required = false)
                    String fieldsJson,
            @ToolParam(name = "page_size", description = "Page size for list operations, default 20", required = false)
                    String pageSize,
            @ToolParam(name = "filter", description = "Filter expression for list_records", required = false)
                    String filter,
            @ToolParam(name = "sort", description = "Sort expression for list_records", required = false)
                    String sort) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            return identity.errorJson();
        }
        try {
            RequestOptions options = identity.options();
            return switch (action.trim().toLowerCase()) {
                case "get_app" -> getApp(appToken, options);
                case "list_tables" -> listTables(appToken, options);
                case "list_records" -> listRecords(appToken, tableId, pageSize, filter, sort, options);
                case "get_record" -> getRecord(appToken, tableId, recordId, options);
                case "create_record" -> createRecord(appToken, tableId, fieldsJson, options);
                case "list_fields" -> listFields(appToken, tableId, options);
                default -> errorJson("unknown_action", "Supported: get_app, list_tables, list_records, get_record, create_record, list_fields");
            };
        } catch (Exception e) {
            logger.error("feishu_bitable_error action={} error={}", action, e.getMessage());
            return errorJson("api_error", e.getMessage());
        }
    }

    private String getApp(String appToken, RequestOptions options) throws Exception {
        if (appToken == null || appToken.isBlank()) {
            return errorJson("missing_params", "app_token is required");
        }
        GetAppReq req = GetAppReq.newBuilder().appToken(appToken).build();
        GetAppResp resp = openApiClient.bitable().v1().app().get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("app", resp.getData().getApp()));
    }

    private String listTables(String appToken, RequestOptions options) throws Exception {
        if (appToken == null || appToken.isBlank()) {
            return errorJson("missing_params", "app_token is required");
        }
        ListAppTableReq req = ListAppTableReq.newBuilder()
                .appToken(appToken)
                .build();
        ListAppTableResp resp = openApiClient.bitable().v1().appTable().list(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tables", resp.getData().getItems());
        result.put("has_more", resp.getData().getHasMore());
        result.put("total", resp.getData().getTotal());
        return toJson(result);
    }

    private String listRecords(String appToken, String tableId, String pageSize, String filter, String sort,
                                RequestOptions options) throws Exception {
        if (appToken == null || appToken.isBlank() || tableId == null || tableId.isBlank()) {
            return errorJson("missing_params", "app_token and table_id are required");
        }
        ListAppTableRecordReq req = ListAppTableRecordReq.newBuilder()
                .appToken(appToken)
                .tableId(tableId)
                .build();
        ListAppTableRecordResp resp = openApiClient.bitable().v1().appTableRecord().list(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", resp.getData().getItems());
        result.put("has_more", resp.getData().getHasMore());
        result.put("total", resp.getData().getTotal());
        return toJson(result);
    }

    private String getRecord(String appToken, String tableId, String recordId, RequestOptions options) throws Exception {
        if (appToken == null || appToken.isBlank() || tableId == null || tableId.isBlank() || recordId == null || recordId.isBlank()) {
            return errorJson("missing_params", "app_token, table_id, and record_id are required");
        }
        GetAppTableRecordReq req = GetAppTableRecordReq.newBuilder()
                .appToken(appToken)
                .tableId(tableId)
                .recordId(recordId)
                .build();
        GetAppTableRecordResp resp = openApiClient.bitable().v1().appTableRecord().get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("record", resp.getData().getRecord()));
    }

    private String createRecord(String appToken, String tableId, String fieldsJson, RequestOptions options) throws Exception {
        if (appToken == null || appToken.isBlank() || tableId == null || tableId.isBlank()) {
            return errorJson("missing_params", "app_token and table_id are required");
        }
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return errorJson("missing_params", "fields_json is required for create");
        }
        java.util.Map<String, Object> fieldsObj = objectMapper.readValue(fieldsJson, java.util.Map.class);
        AppTableRecord record = new AppTableRecord();
        record.setFields(fieldsObj);
        CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                .appToken(appToken)
                .tableId(tableId)
                .appTableRecord(record)
                .build();
        CreateAppTableRecordResp resp = openApiClient.bitable().v1().appTableRecord().create(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("record_id", resp.getData().getRecord().getRecordId(), "record", resp.getData().getRecord()));
    }

    private String listFields(String appToken, String tableId, RequestOptions options) throws Exception {
        if (appToken == null || appToken.isBlank() || tableId == null || tableId.isBlank()) {
            return errorJson("missing_params", "app_token and table_id are required");
        }
        ListAppTableFieldReq req = ListAppTableFieldReq.newBuilder()
                .appToken(appToken)
                .tableId(tableId)
                .build();
        ListAppTableFieldResp resp = openApiClient.bitable().v1().appTableField().list(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", resp.getData().getItems());
        return toJson(result);
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

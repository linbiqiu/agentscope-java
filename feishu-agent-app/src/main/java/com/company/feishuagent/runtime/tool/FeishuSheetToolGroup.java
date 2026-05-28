package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.sheets.v3.model.*;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuSheetToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuSheetToolGroup.class);
    private static final String FEISHU_API_BASE = "https://open.feishu.cn/open-apis";

    private final Client openApiClient;
    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuSheetToolGroup(Client openApiClient, FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_sheet",
            description = "Feishu spreadsheet operations using USER identity (user_access_token). "
                    + "Actions: get (get spreadsheet metadata), query_sheets (list sheets in spreadsheet), "
                    + "read_range (read cell values from a range), write_range (write cell values to a range). "
                    + "Supports spreadsheet tokens from URLs like https://xxx.feishu.cn/sheets/SPREADSHEET_TOKEN. "
                    + "Range format: 'Sheet1!A1:C10' or just 'A1:C10'. "
                    + "The user must have completed OAuth authorization first.")
    public String sheetOperation(
            RuntimeContext ctx,
            @ToolParam(name = "action", description = "Action: get, query_sheets, read_range, write_range")
                    String action,
            @ToolParam(name = "spreadsheet_token", description = "Spreadsheet token (from URL path after /sheets/)", required = false)
                    String spreadsheetToken,
            @ToolParam(name = "sheet_id", description = "Sheet ID within spreadsheet", required = false)
                    String sheetId,
            @ToolParam(name = "range", description = "Cell range, e.g. 'A1:C10' or 'Sheet1!A1:C10'", required = false)
                    String range,
            @ToolParam(name = "values", description = "JSON array of arrays for write_range, e.g. [[\"a\",\"b\"],[\"c\",\"d\"]]", required = false)
                    String values) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            return identity.errorJson();
        }
        try {
            RequestOptions options = identity.options();
            String uat = extractUat(options);
            return switch (action.trim().toLowerCase()) {
                case "get" -> getSpreadsheet(spreadsheetToken, options);
                case "query_sheets" -> querySheets(spreadsheetToken, options);
                case "read_range" -> readRange(spreadsheetToken, range, uat);
                case "write_range" -> writeRange(spreadsheetToken, range, values, uat);
                default -> errorJson("unknown_action", "Supported: get, query_sheets, read_range, write_range");
            };
        } catch (Exception e) {
            logger.error("feishu_sheet_error action={} error={}", action, e.getMessage());
            return errorJson("api_error", e.getMessage());
        }
    }

    private String getSpreadsheet(String spreadsheetToken, RequestOptions options) throws Exception {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            return errorJson("missing_params", "spreadsheet_token is required");
        }
        GetSpreadsheetReq req = GetSpreadsheetReq.newBuilder()
                .spreadsheetToken(spreadsheetToken)
                .build();
        GetSpreadsheetResp resp = openApiClient.sheets().v3().spreadsheet().get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("spreadsheet", resp.getData().getSpreadsheet()));
    }

    private String querySheets(String spreadsheetToken, RequestOptions options) throws Exception {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            return errorJson("missing_params", "spreadsheet_token is required");
        }
        QuerySpreadsheetSheetReq req = QuerySpreadsheetSheetReq.newBuilder()
                .spreadsheetToken(spreadsheetToken)
                .build();
        QuerySpreadsheetSheetResp resp = openApiClient.sheets().v3().spreadsheetSheet().query(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sheets", resp.getData().getSheets());
        return toJson(result);
    }

    private String readRange(String spreadsheetToken, String range, String uat) throws Exception {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            return errorJson("missing_params", "spreadsheet_token is required");
        }
        if (range == null || range.isBlank()) {
            return errorJson("missing_params", "range is required");
        }
        String url = FEISHU_API_BASE + "/sheets/v2/spreadsheets/" + spreadsheetToken + "/values/" + java.net.URLEncoder.encode(range, java.nio.charset.StandardCharsets.UTF_8);
        String responseBody = httpGet(url, uat);
        return responseBody;
    }

    private String writeRange(String spreadsheetToken, String range, String values, String uat) throws Exception {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            return errorJson("missing_params", "spreadsheet_token is required");
        }
        if (range == null || range.isBlank()) {
            return errorJson("missing_params", "range is required");
        }
        if (values == null || values.isBlank()) {
            return errorJson("missing_params", "values is required for write");
        }
        String url = FEISHU_API_BASE + "/sheets/v2/spreadsheets/" + spreadsheetToken + "/values";
        String body = "{\"valueRange\":{\"range\":\"" + escapeJson(range) + "\",\"values\":" + values + "}}";
        String responseBody = httpPut(url, body, uat);
        return responseBody;
    }

    private String extractUat(RequestOptions options) {
        if (options == null) return null;
        try {
            java.lang.reflect.Field headerField = RequestOptions.class.getDeclaredField("headers");
            headerField.setAccessible(true);
            Object headers = headerField.get(options);
            if (headers instanceof Map) {
                Object auth = ((Map<?, ?>) headers).get("Authorization");
                if (auth instanceof String s && s.startsWith("Bearer ")) {
                    return s.substring(7);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String httpGet(String url, String uat) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        if (uat != null) {
            builder.header("Authorization", "Bearer " + uat);
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private String httpPut(String url, String body, String uat) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (uat != null) {
            builder.header("Authorization", "Bearer " + uat);
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return resp.body();
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

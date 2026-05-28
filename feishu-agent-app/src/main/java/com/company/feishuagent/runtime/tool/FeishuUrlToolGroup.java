package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuUrlToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuUrlToolGroup.class);

    private static final Pattern DOCX_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+\\.feishu\\.cn/docx/([a-zA-Z0-9]+)");
    private static final Pattern DOC_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+\\.feishu\\.cn/docs/([a-zA-Z0-9]+)");
    private static final Pattern SHEET_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+\\.feishu\\.cn/sheets/([a-zA-Z0-9]+)");
    private static final Pattern BITABLE_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+\\.feishu\\.cn/base/([a-zA-Z0-9]+)");
    private static final Pattern WIKI_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+\\.feishu\\.cn/wiki/([a-zA-Z0-9]+)");
    private static final Pattern MINUTES_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+\\.feishu\\.cn/minutes/([a-zA-Z0-9]+)");

    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuUrlToolGroup(FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_url_parse",
            description = "Parse a Feishu URL to identify the document type and extract the resource ID. "
                    + "Supports: docx (document), docs (legacy document), sheets (spreadsheet), "
                    + "base (bitable/multi-dimensional table), wiki (knowledge base), minutes (meeting notes). "
                    + "Returns the resource type, ID, and recommended tool+action to use. "
                    + "Call this tool whenever the user mentions a Feishu link or URL.")
    public String parseUrl(
            RuntimeContext ctx,
            @ToolParam(name = "url", description = "The Feishu URL to parse")
                    String url) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            return identity.errorJson();
        }
        if (url == null || url.isBlank()) {
            return errorJson("missing_params", "url is required");
        }
        try {
            return tryParse(url);
        } catch (Exception e) {
            logger.error("feishu_url_parse_error url={} error={}", url, e.getMessage());
            return errorJson("parse_error", e.getMessage());
        }
    }

    private String tryParse(String url) {
        Matcher m;

        m = DOCX_PATTERN.matcher(url);
        if (m.find()) {
            return result("docx", "document_id", m.group(1), "feishu_doc", "get", "document_id=" + m.group(1));
        }

        m = DOC_PATTERN.matcher(url);
        if (m.find()) {
            return result("docs", "document_id", m.group(1), "feishu_doc", "get", "document_id=" + m.group(1));
        }

        m = SHEET_PATTERN.matcher(url);
        if (m.find()) {
            return result("sheets", "spreadsheet_token", m.group(1), "feishu_sheet", "get", "spreadsheet_token=" + m.group(1));
        }

        m = BITABLE_PATTERN.matcher(url);
        if (m.find()) {
            return result("bitable", "app_token", m.group(1), "feishu_bitable", "get_app", "app_token=" + m.group(1));
        }

        m = WIKI_PATTERN.matcher(url);
        if (m.find()) {
            return result("wiki", "node_token", m.group(1), "feishu_doc", "get", "Note: wiki nodes may need node_token resolution first. document_id=" + m.group(1));
        }

        m = MINUTES_PATTERN.matcher(url);
        if (m.find()) {
            return result("minutes", "minute_token", m.group(1), null, null, "Meeting minutes - not directly supported by current tools");
        }

        return errorJson("unrecognized_url", "Cannot identify Feishu resource type from URL: " + url);
    }

    private String result(String resourceType, String idField, String idValue,
                          String recommendedTool, String recommendedAction, String hint) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resource_type", resourceType);
        map.put("id_field", idField);
        map.put("id_value", idValue);
        if (recommendedTool != null) {
            map.put("recommended_tool", recommendedTool);
            map.put("recommended_action", recommendedAction);
        }
        map.put("hint", hint);
        return toJson(map);
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

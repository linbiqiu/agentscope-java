package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.docx.v1.model.*;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuDocToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuDocToolGroup.class);

    private final Client openApiClient;
    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuDocToolGroup(Client openApiClient, FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_doc",
            description = "Feishu document operations using USER identity (user_access_token). "
                    + "Actions: get (read document raw content), create (create new document), "
                    + "list_blocks (list document blocks/children). "
                    + "Supports document IDs from URLs like https://xxx.feishu.cn/docx/DOC_ID. "
                    + "The user must have completed OAuth authorization first.")
    public String docOperation(
            RuntimeContext ctx,
            @ToolParam(name = "action", description = "Action: get, create, list_blocks")
                    String action,
            @ToolParam(name = "document_id", description = "Document ID (from URL path after /docx/)", required = false)
                    String documentId,
            @ToolParam(name = "title", description = "Document title for create action", required = false)
                    String title,
            @ToolParam(name = "folder_token", description = "Folder token to create document in", required = false)
                    String folderToken,
            @ToolParam(name = "block_id", description = "Block ID for list_blocks (use document_id for root)", required = false)
                    String blockId,
            @ToolParam(name = "page_size", description = "Page size for list_blocks, default 500", required = false)
                    String pageSize) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            return identity.errorJson();
        }
        try {
            RequestOptions options = identity.options();
            return switch (action.trim().toLowerCase()) {
                case "get" -> getDocument(documentId, options);
                case "create" -> createDocument(title, folderToken, options);
                case "list_blocks" -> listBlocks(documentId, blockId, pageSize, options);
                default -> errorJson("unknown_action", "Supported: get, create, list_blocks");
            };
        } catch (Exception e) {
            logger.error("feishu_doc_error action={} error={}", action, e.getMessage());
            return errorJson("api_error", e.getMessage());
        }
    }

    private String getDocument(String documentId, RequestOptions options) throws Exception {
        if (documentId == null || documentId.isBlank()) {
            return errorJson("missing_params", "document_id is required");
        }
        RawContentDocumentReq req = RawContentDocumentReq.newBuilder()
                .documentId(documentId)
                .build();
        RawContentDocumentResp resp = openApiClient.docx().v1().document().rawContent(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document_id", documentId);
        result.put("content", resp.getData().getContent());
        return toJson(result);
    }

    private String createDocument(String title, String folderToken, RequestOptions options) throws Exception {
        String docTitle = (title != null && !title.isBlank()) ? title : "新建文档";
        CreateDocumentReqBody body = CreateDocumentReqBody.newBuilder()
                .title(docTitle)
                .folderToken(folderToken)
                .build();
        CreateDocumentReq req = CreateDocumentReq.newBuilder()
                .createDocumentReqBody(body)
                .build();
        CreateDocumentResp resp = openApiClient.docx().v1().document().create(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document_id", resp.getData().getDocument().getDocumentId());
        result.put("title", resp.getData().getDocument().getTitle());
        result.put("url", "https://feishu.cn/docx/" + resp.getData().getDocument().getDocumentId());
        return toJson(result);
    }

    private String listBlocks(String documentId, String blockId, String pageSize, RequestOptions options)
            throws Exception {
        if (documentId == null || documentId.isBlank()) {
            return errorJson("missing_params", "document_id is required");
        }
        String bid = (blockId != null && !blockId.isBlank()) ? blockId : documentId;
        GetDocumentBlockChildrenReq req =
                GetDocumentBlockChildrenReq.newBuilder()
                        .documentId(documentId)
                        .blockId(bid)
                        .build();
        GetDocumentBlockChildrenResp resp = openApiClient.docx().v1().documentBlockChildren()
                .get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", resp.getData().getItems());
        result.put("page_token", resp.getData().getPageToken());
        result.put("has_more", resp.getData().getHasMore());
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

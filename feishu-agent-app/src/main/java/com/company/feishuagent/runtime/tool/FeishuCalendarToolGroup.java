package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.runtime.tool.FeishuApiToolHelper.IdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.calendar.v4.model.*;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuCalendarToolGroup {

    private static final Logger logger = LoggerFactory.getLogger(FeishuCalendarToolGroup.class);
    private static final String PRIMARY_CALENDAR = "primary";

    private final Client openApiClient;
    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    public FeishuCalendarToolGroup(Client openApiClient, FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.openApiClient = openApiClient;
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "feishu_calendar_event",
            description = "Feishu calendar event management using USER identity (user_access_token). "
                    + "Actions: list (query events by time range), get (get event by ID). "
                    + "The user must have completed OAuth authorization first. "
                    + "Time format: Unix timestamp in seconds (string), e.g. '1748304000'")
    public String calendarEvent(
            RuntimeContext ctx,
            @ToolParam(name = "action", description = "Action: list (query by time range), get (get event detail)")
                    String action,
            @ToolParam(
                            name = "calendar_id",
                            description = "Calendar ID. Use 'primary' for the primary calendar",
                            required = false)
                    String calendarId,
            @ToolParam(
                            name = "start_time",
                            description = "Start time (Unix timestamp seconds string), required for list",
                            required = false)
                    String startTime,
            @ToolParam(
                            name = "end_time",
                            description = "End time (Unix timestamp seconds string), required for list",
                            required = false)
                    String endTime,
            @ToolParam(name = "event_id", description = "Event ID, required for get", required = false)
                    String eventId) {
        IdentityResult identity = FeishuApiToolHelper.resolveIdentityAndUat(ctx, feishuApiClient);
        if (!identity.isOk()) {
            String errJson = identity.errorJson();
            logger.info("feishu_calendar_oauth_blocked action={} result={}", action, errJson);
            return errJson;
        }
        try {
            String calId = (calendarId != null && !calendarId.isBlank()) ? calendarId : PRIMARY_CALENDAR;
            RequestOptions options = identity.options();
            return switch (action.trim().toLowerCase()) {
                case "list" -> listEvents(calId, startTime, endTime, options);
                case "get" -> getEvent(calId, eventId, options);
                default -> errorJson("unknown_action", "Supported: list, get");
            };
        } catch (Exception e) {
            logger.error("feishu_calendar_error action={} error={}", action, e.getMessage());
            return errorJson("api_error", e.getMessage());
        }
    }

    private String listEvents(String calendarId, String startTime, String endTime, RequestOptions options)
            throws Exception {
        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            return errorJson("missing_params", "start_time and end_time are required");
        }
        logger.info("feishu_calendar_list calendarId={} start_time={} end_time={}", calendarId, startTime, endTime);
        ListCalendarEventReq req =
                ListCalendarEventReq.newBuilder().calendarId(calendarId).startTime(startTime).endTime(endTime).build();
        ListCalendarEventResp resp = openApiClient.calendar().v4().calendarEvent().list(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        CalendarEvent[] items = resp.getData().getItems();
        java.util.List<java.util.Map<String, Object>> filtered = new java.util.ArrayList<>();
        if (items != null) {
            for (CalendarEvent event : items) {
                if ("cancelled".equals(event.getStatus())) {
                    continue;
                }
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("event_id", event.getEventId());
                map.put("summary", event.getSummary());
                map.put("status", event.getStatus());
                if (event.getStartTime() != null) {
                    map.put("start_time", event.getStartTime().getTimestamp());
                }
                if (event.getEndTime() != null) {
                    map.put("end_time", event.getEndTime().getTimestamp());
                }
                if (event.getVchat() != null) {
                    map.put("meeting_url", event.getVchat().getMeetingUrl());
                }
                map.put("visibility", event.getVisibility());
                filtered.add(map);
            }
        }
        logger.info("feishu_calendar_list total={} filtered={}", items != null ? items.length : 0, filtered.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", filtered);
        result.put("has_more", resp.getData().getHasMore());
        return toJson(result);
    }

    private String getEvent(String calendarId, String eventId, RequestOptions options) throws Exception {
        if (eventId == null || eventId.isBlank()) {
            return errorJson("missing_params", "event_id is required");
        }
        GetCalendarEventReq req =
                GetCalendarEventReq.newBuilder().calendarId(calendarId).eventId(eventId).build();
        GetCalendarEventResp resp = openApiClient.calendar().v4().calendarEvent().get(req, options);
        if (!resp.success()) {
            return errorJson("api_error", resp.getCode() + ": " + resp.getMsg());
        }
        return toJson(Map.of("event", resp.getData().getEvent()));
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

package com.company.feishuagent.feishu.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record FeishuEventRequest(String type, String challenge, String token, @Valid FeishuEvent event) {

    public record FeishuEvent(
            @NotBlank String eventId,
            String messageType,
            String chatType,
            @NotBlank String userOpenId,
            String chatId,
            @NotBlank String text,
            String agentCode,
            String requestedSkill,
            @Valid UserIdentity actor,
            List<@Valid UserIdentity> mentions,
            String identityContextJson,
            String rootId) {}

    public record UserIdentity(String openId, String unionId, String userId, String mobile, String employeeNo, String name, String email) {}
}

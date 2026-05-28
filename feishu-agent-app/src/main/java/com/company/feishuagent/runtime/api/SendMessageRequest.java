package com.company.feishuagent.runtime.api;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank String userId,
        String conversationId,
        String chatType,
        @NotBlank String message,
        String requestedSkill,
        String dispatchMode,
        String identityContextJson,
        EnterpriseIdentity enterpriseIdentity) {

    public SendMessageRequest(
            @NotBlank String userId,
            String conversationId,
            String chatType,
            @NotBlank String message,
            String requestedSkill,
            String dispatchMode,
            String identityContextJson) {
        this(userId, conversationId, chatType, message, requestedSkill, dispatchMode, identityContextJson, null);
    }
}

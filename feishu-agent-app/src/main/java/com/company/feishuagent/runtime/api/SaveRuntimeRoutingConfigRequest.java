package com.company.feishuagent.runtime.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveRuntimeRoutingConfigRequest(
        @NotBlank String operatorId,
        @NotBlank String provider,
        @NotBlank String model,
        String apiKey,
        String baseUrl,
        String fallbackModel,
        @NotBlank String dispatchMode,
        String manualSkill,
        @NotNull @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @NotNull @Min(1) @Max(32768) Integer maxTokens) {}

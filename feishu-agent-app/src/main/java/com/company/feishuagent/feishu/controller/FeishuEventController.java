package com.company.feishuagent.feishu.controller;

import com.company.feishuagent.feishu.api.FeishuEventRequest;
import com.company.feishuagent.feishu.api.FeishuEventResponse;
import com.company.feishuagent.feishu.service.FeishuEventService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feishu")
public class FeishuEventController {

    private final FeishuEventService feishuEventService;
    private final String signingSecret;
    private final long signatureMaxSkewSeconds;

    public FeishuEventController(
            FeishuEventService feishuEventService,
            @Value("${feishu.runtime.signing-secret:}") String signingSecret,
            @Value("${feishu.runtime.signature-max-skew-seconds:300}") long signatureMaxSkewSeconds) {
        this.feishuEventService = feishuEventService;
        this.signingSecret = signingSecret;
        this.signatureMaxSkewSeconds = signatureMaxSkewSeconds;
    }

    @PostMapping("/events")
    public FeishuEventResponse events(
            @Valid @RequestBody FeishuEventRequest request,
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature) {
        if (!isSignatureValid(request, timestamp, signature)) {
            return new FeishuEventResponse(false, null, null, "invalid signature");
        }
        return feishuEventService.handleEvent(request);
    }

    private boolean isSignatureValid(FeishuEventRequest request, String timestamp, String signature) {
        if (!StringUtils.hasText(signingSecret) || "url_verification".equals(request.type())) {
            return true;
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return false;
        }
        long requestSecond;
        try {
            requestSecond = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return false;
        }
        long nowSecond = System.currentTimeMillis() / 1000;
        if (Math.abs(nowSecond - requestSecond) > Math.max(0L, signatureMaxSkewSeconds)) {
            return false;
        }
        String payload = timestamp + request.type() + (request.token() == null ? "" : request.token());
        return signature.equals(hmacSha256Hex(signingSecret, payload));
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(signed.length * 2);
            for (byte item : signed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

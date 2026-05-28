package com.company.feishuagent.runtime.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnterpriseToolGroupTest {

    private EnterpriseToolGroup toolGroup;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        toolGroup = new EnterpriseToolGroup(objectMapper);
    }

    @Test
    void getUserProfileReturnsIdentityWhenAvailable() {
        EnterpriseIdentity identity = new EnterpriseIdentity(
                "ou_123", "un_456", "uid_789", "Zhang San",
                "13800138000", "EMP001", "zhang@example.com", "dep_01");
        RuntimeContext ctx = RuntimeContext.builder()
                .put(EnterpriseIdentity.class, identity)
                .build();

        String result = toolGroup.getUserProfile(ctx);

        assertNotNull(result);
        assertTrue(result.contains("Zhang San"));
        assertTrue(result.contains("13800138000"));
        assertTrue(result.contains("EMP001"));
        assertTrue(result.contains("\"isComplete\":true"));
    }

    @Test
    void getUserProfileReturnsErrorWhenIdentityNotAvailable() {
        RuntimeContext ctx = RuntimeContext.empty();

        String result = toolGroup.getUserProfile(ctx);

        assertNotNull(result);
        assertTrue(result.contains("identity_not_available"));
    }

    @Test
    void getUserProfileReturnsErrorWhenContextIsNull() {
        String result = toolGroup.getUserProfile(null);

        assertTrue(result.contains("identity_not_available"));
    }

    @Test
    void getUserProfileReadsFromTypedKey() {
        EnterpriseIdentity identity = new EnterpriseIdentity(
                "ou_abc", null, null, "Li Si",
                "13900139000", null, null, null);
        RuntimeContext ctx = RuntimeContext.builder()
                .put(EnterpriseIdentity.class, identity)
                .build();

        String result = toolGroup.getUserProfile(ctx);

        assertTrue(result.contains("Li Si"));
        assertTrue(result.contains("13900139000"));
        assertTrue(result.contains("\"isComplete\":false"));
    }

    @Test
    void checkIdentityFieldReturnsAvailableForMobile() {
        EnterpriseIdentity identity = new EnterpriseIdentity(
                "ou_1", null, null, null, "13800138000", null, null, null);
        RuntimeContext ctx = RuntimeContext.builder()
                .put(EnterpriseIdentity.class, identity)
                .build();

        String result = toolGroup.checkIdentityField(ctx, "mobile");

        assertTrue(result.contains("\"available\":true"));
        assertTrue(result.contains("13800138000"));
    }

    @Test
    void checkIdentityFieldReturnsNotAvailableForMissingField() {
        EnterpriseIdentity identity = new EnterpriseIdentity(
                "ou_1", null, null, null, null, null, null, null);
        RuntimeContext ctx = RuntimeContext.builder()
                .put(EnterpriseIdentity.class, identity)
                .build();

        String result = toolGroup.checkIdentityField(ctx, "employeeno");

        assertTrue(result.contains("\"available\":false"));
    }

    @Test
    void checkIdentityFieldReturnsErrorForUnknownField() {
        EnterpriseIdentity identity = new EnterpriseIdentity(
                "ou_1", null, null, "Test", null, null, null, null);
        RuntimeContext ctx = RuntimeContext.builder()
                .put(EnterpriseIdentity.class, identity)
                .build();

        String result = toolGroup.checkIdentityField(ctx, "unknownField");

        assertTrue(result.contains("unknown_field"));
    }

    @Test
    void checkIdentityFieldReturnsErrorWhenIdentityNotAvailable() {
        RuntimeContext ctx = RuntimeContext.empty();

        String result = toolGroup.checkIdentityField(ctx, "mobile");

        assertTrue(result.contains("identity_not_available"));
    }
}

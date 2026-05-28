package com.company.feishuagent.runtime.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import org.junit.jupiter.api.Test;

class RuntimeSessionConfigTest {

    @Test
    void fallsBackToJsonSessionWhenRedisNotRequired() {
        RuntimeSessionConfig config = new RuntimeSessionConfig();

        Session session = config.runtimeSession(false, "", "feishu:session:", 86400, false);

        assertTrue(session instanceof JsonSession);
    }

    @Test
    void failsFastWhenRedisRequiredButUrlMissing() {
        RuntimeSessionConfig config = new RuntimeSessionConfig();

        assertThrows(
                IllegalStateException.class,
                () -> config.runtimeSession(true, "", "feishu:session:", 86400, true));
    }
}

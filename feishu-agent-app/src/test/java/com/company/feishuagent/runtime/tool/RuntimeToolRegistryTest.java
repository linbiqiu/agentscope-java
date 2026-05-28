package com.company.feishuagent.runtime.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.feishuagent.runtime.skill.RuntimeSkillPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeToolRegistryTest {

    private RuntimeToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RuntimeToolRegistry(new ObjectMapper());
    }

    @Test
    void registerToolsRegistersEnterpriseTools() {
        Toolkit toolkit = new Toolkit();
        RuntimeSkillPlan plan = new RuntimeSkillPlan("default", List.of("default"));

        registry.registerTools(toolkit, plan);

        assertFalse(toolkit.getToolSchemas().isEmpty());
        boolean hasProfile = toolkit.getToolSchemas().stream()
                .anyMatch(s -> "enterprise_get_user_profile".equals(s.getName()));
        assertTrue(hasProfile, "Should register enterprise_get_user_profile tool");
        boolean hasCheck = toolkit.getToolSchemas().stream()
                .anyMatch(s -> "enterprise_check_identity_field".equals(s.getName()));
        assertTrue(hasCheck, "Should register enterprise_check_identity_field tool");
    }

    @Test
    void resolveToolsForPlanReturnsAllRegisteredGroups() {
        RuntimeSkillPlan plan = new RuntimeSkillPlan("default", List.of("default"));

        List<String> tools = registry.resolveToolsForPlan(plan);

        assertTrue(tools.contains("enterprise"));
    }

    @Test
    void registerToolsWithCustomProvider() {
        Toolkit toolkit = new Toolkit();
        registry.register("custom", tkit -> {
            tkit.registerTool(new Object() {
                @io.agentscope.core.tool.Tool(name = "custom_tool", description = "A custom tool")
                public String run() { return "ok"; }
            });
        });

        registry.registerTools(toolkit, new RuntimeSkillPlan("", List.of()));

        boolean hasCustom = toolkit.getToolSchemas().stream()
                .anyMatch(s -> "custom_tool".equals(s.getName()));
        assertTrue(hasCustom, "Should register custom tool");
        assertEquals(3, toolkit.getToolSchemas().size());
    }
}

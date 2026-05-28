package com.company.feishuagent.runtime.tool;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import io.agentscope.core.tool.Toolkit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeToolRegistry.class);

    private final Map<String, ToolProvider> toolProviders = new LinkedHashMap<>();

    public RuntimeToolRegistry(ObjectMapper objectMapper) {
        register("enterprise", toolkit -> toolkit.registerTool(new EnterpriseToolGroup(objectMapper)));
    }

    public RuntimeToolRegistry(ObjectMapper objectMapper, Client openApiClient, FeishuApiClient feishuApiClient) {
        register("enterprise", toolkit -> toolkit.registerTool(new EnterpriseToolGroup(objectMapper)));
        register("feishu-calendar", toolkit -> toolkit.registerTool(
                new FeishuCalendarToolGroup(openApiClient, feishuApiClient, objectMapper)));
        register("feishu-im", toolkit -> toolkit.registerTool(
                new FeishuImToolGroup(openApiClient, feishuApiClient, objectMapper)));
        register("feishu-contact", toolkit -> toolkit.registerTool(
                new FeishuContactToolGroup(openApiClient, feishuApiClient, objectMapper)));
        register("feishu-doc", toolkit -> toolkit.registerTool(
                new FeishuDocToolGroup(openApiClient, feishuApiClient, objectMapper)));
        register("feishu-sheet", toolkit -> toolkit.registerTool(
                new FeishuSheetToolGroup(openApiClient, feishuApiClient, objectMapper)));
        register("feishu-bitable", toolkit -> toolkit.registerTool(
                new FeishuBitableToolGroup(openApiClient, feishuApiClient, objectMapper)));
        register("feishu-url-parse", toolkit -> toolkit.registerTool(
                new FeishuUrlToolGroup(feishuApiClient, objectMapper)));
    }

    public void register(String toolGroupName, ToolProvider provider) {
        toolProviders.put(toolGroupName, provider);
    }

    public void registerTools(Toolkit toolkit, RuntimeSkillPlan skillPlan) {
        List<String> requestedTools = resolveToolsForPlan(skillPlan);
        for (Map.Entry<String, ToolProvider> entry : toolProviders.entrySet()) {
            if (requestedTools.contains(entry.getKey())) {
                entry.getValue().register(toolkit);
                logger.info("runtime_tool_registered group={}", entry.getKey());
            }
        }
    }

    List<String> resolveToolsForPlan(RuntimeSkillPlan skillPlan) {
        return List.copyOf(toolProviders.keySet());
    }

    public interface ToolProvider {
        void register(Toolkit toolkit);
    }
}

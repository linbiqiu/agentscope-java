package com.company.feishuagent.runtime.service;

import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.DockerFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxDistributedOptions;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeAgentFactoryImpl implements RuntimeAgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeAgentFactoryImpl.class);
    private static final String DEFAULT_SKILL = "default";

    private final Path skillRepositoryDir;
    private final Path workspaceDir;
    private final boolean sandboxEnabled;
    private final String sandboxDockerImage;

    public RuntimeAgentFactoryImpl(
            @Value("${feishu.runtime.skill-repo.dir:}") String skillRepositoryDir,
            @Value("${feishu.runtime.workspace-dir:.agentscope/feishu-runtime}") String workspaceDir,
            @Value("${feishu.runtime.sandbox.enabled:true}") boolean sandboxEnabled,
            @Value("${feishu.runtime.sandbox.docker-image:}") String sandboxDockerImage) {
        this.skillRepositoryDir =
                skillRepositoryDir == null || skillRepositoryDir.isBlank()
                        ? null
                        : Path.of(skillRepositoryDir.trim()).toAbsolutePath().normalize();
        this.workspaceDir = Path.of(workspaceDir).toAbsolutePath().normalize();
        this.sandboxEnabled = sandboxEnabled;
        this.sandboxDockerImage = sandboxDockerImage;
    }

    @Override
    public RuntimeAgentHolder create(RuntimeRoutingConfig config, String requestedSkill) {
        Model model = createModel(config);
        Toolkit toolkit = new Toolkit();
        String activatedSkill = normalizeRequestedSkill(requestedSkill);

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("feishu-runtime-agent")
                .model(model)
                .workspace(workspaceDir)
                .toolkit(toolkit)
                .session(null)
                .enablePendingToolRecovery(true)
                .maxIters(15)
                .disableSubagents();

        if (skillRepositoryDir != null) {
            AgentSkillRepository repository = new FileSystemSkillRepository(skillRepositoryDir, false);
            builder.skillRepository(repository);
            logger.info(
                    "runtime_skill_repo_loaded repoDir={} requestedSkill={} activatedSkill={}",
                    skillRepositoryDir,
                    requestedSkill,
                    activatedSkill);
        }

        if (sandboxEnabled) {
            DockerFilesystemSpec filesystemSpec = new DockerFilesystemSpec();
            filesystemSpec.isolationScope(IsolationScope.USER);
            if (sandboxDockerImage != null && !sandboxDockerImage.isBlank()) {
                filesystemSpec.image(sandboxDockerImage.trim());
            }
            builder.filesystem(filesystemSpec);
            builder.sandboxDistributed(
                    SandboxDistributedOptions.builder().requireDistributed(false).build());
        } else {
            builder.filesystem(new LocalFilesystemSpec());
        }

        ReActAgent delegate = builder.build().getDelegate();
        return new RuntimeAgentHolder(delegate, activatedSkill);
    }

    private String normalizeRequestedSkill(String requestedSkill) {
        if (requestedSkill == null || requestedSkill.isBlank()) {
            return DEFAULT_SKILL;
        }
        return requestedSkill.trim();
    }

    private Model createModel(RuntimeRoutingConfig config) {
        String provider = normalize(config.provider());
        GenerateOptions options = GenerateOptions.builder()
                .temperature(config.temperature())
                .maxTokens(config.maxTokens())
                .build();
        if ("openai".equals(provider) || "einwin".equals(provider)) {
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .modelName(config.model())
                    .stream(false)
                    .generateOptions(options);
            if (notBlank(config.apiKey())) {
                builder.apiKey(config.apiKey());
            }
            if (notBlank(config.baseUrl())) {
                builder.baseUrl(config.baseUrl());
            }
            return builder.build();
        }
        if ("anthropic".equals(provider)) {
            AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                    .modelName(config.model())
                    .stream(false)
                    .defaultOptions(options);
            if (notBlank(config.apiKey())) {
                builder.apiKey(config.apiKey());
            }
            if (notBlank(config.baseUrl())) {
                builder.baseUrl(config.baseUrl());
            }
            return builder.build();
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }

    private String normalize(String provider) {
        return provider == null ? "anthropic" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

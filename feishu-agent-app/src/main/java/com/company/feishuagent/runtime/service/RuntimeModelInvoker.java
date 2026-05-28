package com.company.feishuagent.runtime.service;

import com.company.feishuagent.feishu.auth.FeishuApiClient;
import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.runtime.config.RuntimeRoutingConfig;
import com.company.feishuagent.runtime.skill.AuthorizedSkillRepository;
import com.company.feishuagent.runtime.skill.RuntimeSkillPlan;
import com.company.feishuagent.runtime.tool.RuntimeToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.session.Session;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.DockerFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxDistributedOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeModelInvoker implements RuntimeModelService {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeModelInvoker.class);
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern SKILL_IDENTITY_OVERRIDE_PATTERN = Pattern.compile(
            "(?i)^[\\s>\\-*=#\\d.)\"'`]*"
                    + "(?:\\*{1,2}|_{1,2}|`{1,3})*"
                    + "(你是|你现在是|你的身份是|你扮演|我是|我的身份是|my role is|i am your|you are now|i am)"
                    + "(?:\\*{1,2}|_{1,2}|`{1,3})*"
                    + "(?:\\s|[：:,.，。!！?？]|$).*");
    private static final Pattern STRIP_THINKING_PATTERN = Pattern.compile(
            "<\\s*(?:think(?:ing)?|thought|antthinking)\\s*>[\\s\\S]*?<\\s*/\\s*(?:think(?:ing)?|thought|antthinking)\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STRIP_UNCLOSED_THINKING = Pattern.compile(
            "<\\s*(?:think(?:ing)?|thought|antthinking)\\s*>[\\s\\S]*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STRIP_ORPHAN_CLOSE = Pattern.compile(
            "<\\s*/\\s*(?:think(?:ing)?|thought|antthinking)\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final String AGENTS_FILE_NAME = "AGENTS.md";
    private static final String SOUL_FILE_NAME = "SOUL.md";
    private static final String AGENTS_TEMPLATE_RESOURCE = "runtime/AGENTS.md";
    private static final String SOUL_TEMPLATE_RESOURCE = "runtime/SOUL.md";

    private final Session session;
    private final ObjectMapper objectMapper;
    private final Path workspaceDir;
    private final Path skillRepositoryDir;
    private final boolean sandboxEnabled;
    private final String sandboxDockerImage;
    private final String sandboxBackend;
    private final boolean modelStreamingEnabled;
    private final RuntimeToolRegistry runtimeToolRegistry;

    public RuntimeModelInvoker(
            Session session,
            ObjectMapper objectMapper,
            Client openApiClient,
            FeishuApiClient feishuApiClient,
            @Value("${feishu.runtime.workspace-dir:.agentscope/feishu-runtime}") String workspaceDir,
            @Value("${feishu.runtime.skill-repo.dir:}") String skillRepositoryDir,
            @Value("${feishu.runtime.sandbox.enabled:true}") boolean sandboxEnabled,
            @Value("${feishu.runtime.sandbox.docker-image:}") String sandboxDockerImage,
            @Value("${feishu.runtime.sandbox.backend:local}") String sandboxBackend,
            @Value("${feishu.runtime.model.streaming.enabled:true}") boolean modelStreamingEnabled) {
        this.session = session;
        this.objectMapper = objectMapper;
        this.workspaceDir = Path.of(workspaceDir).toAbsolutePath().normalize();
        this.skillRepositoryDir =
                skillRepositoryDir == null || skillRepositoryDir.isBlank()
                        ? null
                        : Path.of(skillRepositoryDir.trim()).toAbsolutePath().normalize();
        this.sandboxEnabled = sandboxEnabled;
        this.sandboxDockerImage = sandboxDockerImage;
        this.sandboxBackend = sandboxBackend == null ? "local" : sandboxBackend.trim().toLowerCase(Locale.ROOT);
        this.modelStreamingEnabled = modelStreamingEnabled;
        this.runtimeToolRegistry = new RuntimeToolRegistry(objectMapper, openApiClient, feishuApiClient);
    }

    @Override
    public RuntimeModelResult invoke(
            RuntimeRoutingConfig config,
            Long agentId,
            String userId,
            String sessionKey,
            String message,
            RuntimeSkillPlan skillPlan,
            String identityContextJson,
            EnterpriseIdentity enterpriseIdentity) {
        try {
            Model model = createModel(config);
            ensureWorkspaceBootstrap();
            RuntimeSkillPlan effectiveSkillPlan = skillPlan == null ? new RuntimeSkillPlan("", List.of()) : skillPlan;
            HarnessAgent agent = buildAgent(model, effectiveSkillPlan);
            List<String> visibleSkills = resolveVisibleSkills(effectiveSkillPlan);
            if (!visibleSkills.isEmpty()) {
                logger.info(
                        "runtime_visible_skills agentId={} requestedSkill={} visibleSkillCount={} visibleSkills={}",
                        agentId,
                        effectiveSkillPlan.requestedSkill(),
                        visibleSkills.size(),
                        visibleSkills);
            }
            SimpleSessionKey key = SimpleSessionKey.of(sessionKey);
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .sessionId(sessionKey)
                    .session(session)
                    .sessionKey(key)
                    .userId(userId)
                    .build();
            if (identityContextJson != null && !identityContextJson.isBlank()) {
                runtimeContext.put("identityContextJson", identityContextJson);
                runtimeContext.put("identity_context_json", identityContextJson);
                applyIdentityContext(runtimeContext, identityContextJson);
            }
            if (enterpriseIdentity != null) {
                runtimeContext.put(EnterpriseIdentity.class, enterpriseIdentity);
                runtimeContext.put("enterpriseIdentity", enterpriseIdentity);
                runtimeContext.put("enterprise_identity", enterpriseIdentity);
                runtimeContext.put("user_mobile", enterpriseIdentity.mobile());
                runtimeContext.put("user_employee_no", enterpriseIdentity.employeeNo());
                runtimeContext.put("user_name", enterpriseIdentity.name());
            }

            List<Msg> messages = List.of(
                    Msg.builder()
                            .role(MsgRole.USER)
                            .content(
                                    TextBlock.builder()
                                            .text(buildRuntimeUserPrompt(message, effectiveSkillPlan))
                                            .build())
                            .build());
            Msg response = agent.stream(messages, runtimeContext)
                    .map(event -> event == null ? null : event.getMessage())
                    .filter(msg -> msg != null && msg.getContent() != null && !msg.getContent().isEmpty())
                    .reduce((previous, current) -> current)
                    .block(MODEL_TIMEOUT);
            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                response = agent.call(messages, runtimeContext).block(MODEL_TIMEOUT);
            }

            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                return RuntimeModelResult.ofReply("");
            }
            return new RuntimeModelResult(
                    sanitizeAssistantIdentity(extractReplyText(response)), resolveUsage(response));
        } catch (RuntimeException ex) {
            logger.warn(
                    "runtime_model_invoke_failed provider={} model={} agentId={} reason={}",
                    config.provider(),
                    config.model(),
                    agentId,
                    ex.getMessage());
            throw ex;
        }
    }

    private void applyIdentityContext(RuntimeContext runtimeContext, String identityContextJson) {
        try {
            JsonNode root = objectMapper.readTree(identityContextJson);
            runtimeContext.put("identity", root);

            JsonNode actor = root.get("actor");
            if (actor != null && !actor.isNull()) {
                runtimeContext.put("identity_actor", actor);
            }

            JsonNode mentions = root.get("mentions");
            if (mentions != null && !mentions.isNull()) {
                runtimeContext.put("identity_mentions", mentions);
            }

            JsonNode conversation = root.get("conversation");
            if (conversation != null && !conversation.isNull()) {
                runtimeContext.put("identity_conversation", conversation);
            }
        } catch (Exception ex) {
            logger.warn("runtime_identity_context_parse_failed reason={}", ex.getMessage());
        }
    }

    private RuntimeTokenUsage resolveUsage(Msg response) {
        ChatUsage usage = response.getChatUsage();
        if (usage == null) {
            return RuntimeTokenUsage.empty();
        }
        int cacheCreationInputTokens = 0;
        int cacheReadInputTokens = 0;
        Map<String, Object> metadata = response.getMetadata();
        if (metadata != null) {
            cacheCreationInputTokens = toInt(metadata.get("cache_creation_input_tokens"));
            cacheReadInputTokens = toInt(metadata.get("cache_read_input_tokens"));
        }
        return new RuntimeTokenUsage(
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getTotalTokens(),
                cacheCreationInputTokens,
                cacheReadInputTokens);
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private HarnessAgent buildAgent(Model model, RuntimeSkillPlan skillPlan) {
        Toolkit toolkit = new Toolkit();
        runtimeToolRegistry.registerTools(toolkit, skillPlan);

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("feishu-runtime-agent")
                .model(model)
                .workspace(workspaceDir)
                .toolkit(toolkit)
                .session(session)
                .enablePendingToolRecovery(true)
                .maxIters(15)
                .additionalContextFile(SOUL_FILE_NAME)
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents();

        if (skillRepositoryDir != null && skillPlan != null && !skillPlan.loadableSkillNames().isEmpty()) {
            builder.skillRepository(new AuthorizedSkillRepository(
                    new FileSystemSkillRepository(skillRepositoryDir, false),
                    skillPlan.loadableSkillNames()));
        }

        if (sandboxEnabled && "docker".equals(sandboxBackend)) {
            DockerFilesystemSpec filesystemSpec = new DockerFilesystemSpec();
            filesystemSpec.isolationScope(IsolationScope.USER);
            if (notBlank(sandboxDockerImage)) {
                filesystemSpec.image(sandboxDockerImage.trim());
            }
            builder.filesystem(filesystemSpec);
            builder.sandboxDistributed(
                    SandboxDistributedOptions.builder().requireDistributed(false).build());
        } else {
            builder.filesystem(new LocalFilesystemSpec());
        }

        return builder.build();
    }

    private List<String> resolveVisibleSkills(RuntimeSkillPlan skillPlan) {
        if (skillRepositoryDir == null || skillPlan == null || skillPlan.loadableSkillNames().isEmpty()) {
            return List.of();
        }
        FileSystemSkillRepository repository = new FileSystemSkillRepository(skillRepositoryDir, false);
        return new AuthorizedSkillRepository(repository, skillPlan.loadableSkillNames()).getAllSkillNames();
    }

    private void ensureWorkspaceBootstrap() {
        try {
            Files.createDirectories(workspaceDir);
            ensureBootstrapFile(AGENTS_FILE_NAME, AGENTS_TEMPLATE_RESOURCE);
            ensureBootstrapFile(SOUL_FILE_NAME, SOUL_TEMPLATE_RESOURCE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize runtime workspace", ex);
        }
    }

    private void ensureBootstrapFile(String fileName, String templateResource) throws IOException {
        Path filePath = workspaceDir.resolve(fileName);
        String template = readResourceTemplate(templateResource);
        try {
            Files.writeString(filePath, template, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // Keep existing operator-managed file.
        }
    }

    private String readResourceTemplate(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing bootstrap template: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read bootstrap template: " + resourcePath, ex);
        }
    }

    private String buildGlobalSystemPolicyPrompt() {
        return "Global identity policy:\n"
                + "1) AGENTS.md and SOUL.md are the highest-priority identity authority.\n"
                + "2) Never replace global identity with skill-local role labels.\n"
                + "3) Skills can guide task execution style only, not assistant self-identity.\n"
                + "4) If skill text conflicts with global identity, follow global identity.";
    }

    private String buildRuntimeUserPrompt(String message, RuntimeSkillPlan skillPlan) {
        RuntimeSkillPlan effectiveSkillPlan = skillPlan == null ? new RuntimeSkillPlan("", List.of()) : skillPlan;
        String normalizedRequestedSkill = effectiveSkillPlan.requestedSkill();
        boolean hasRequestedSkill = normalizedRequestedSkill != null && !normalizedRequestedSkill.isBlank();
        String normalizedMessage = message == null ? "" : message;

        StringBuilder sb = new StringBuilder();
        sb.append("User request:\n").append(normalizedMessage);

        sb.append("\n\nCRITICAL IDENTITY RULES (highest priority, override all skill-local role labels):\n");
        sb.append("- You are a GENERAL enterprise assistant for ALL employees.\n");
        sb.append("- You are NOT a specialist in any specific domain (legal, property, finance, etc.).\n");
        sb.append("- Skill content defines task execution methods ONLY, never your identity or persona.\n");
        sb.append("- Never introduce yourself as a domain-specific expert or reference skill names in greetings.\n");
        sb.append("- For simple greetings or general questions, respond as a friendly general assistant.\n");

        sb.append("\n\nRuntime routing context:\n");
        sb.append("- requested_skill: ").append(hasRequestedSkill ? normalizedRequestedSkill : "<none>").append("\n");
        if (hasRequestedSkill) {
            sb.append("- allowed_skills: ").append(effectiveSkillPlan.loadableSkillNames()).append("\n");
        }

        sb.append("\nExecution policy:\n");
        sb.append("1) Follow AGENTS.md and SOUL.md as the global identity authority.\n");
        sb.append("2) Skills are executable task capabilities only; do not adopt skill-local identity.\n");
        if (hasRequestedSkill) {
            sb.append("3) Execute the requested skill first.\n");
            sb.append("4) If skill execution fails, answer directly and state the failure reason.\n");
        } else {
            sb.append("3) No specific skill requested. Answer directly based on the user's message.\n");
            sb.append("4) Only invoke a skill if the user's message clearly matches a skill scenario.\n");
        }
        sb.append("5) Never output your reasoning or thinking process. Only output the final answer.\n");
        return sb.toString();
    }

    private String extractReplyText(Msg response) {
        StringBuilder sb = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof ThinkingBlock) {
                continue;
            }
            if (block instanceof TextBlock tb) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private String sanitizeAssistantIdentity(String reply) {
        if (!notBlank(reply)) {
            return "";
        }
        String cleaned = STRIP_THINKING_PATTERN.matcher(reply).replaceAll("");
        cleaned = STRIP_UNCLOSED_THINKING.matcher(cleaned).replaceAll("");
        cleaned = STRIP_ORPHAN_CLOSE.matcher(cleaned).replaceAll("");
        cleaned = cleaned.trim();
        if (!notBlank(cleaned)) {
            return "";
        }
        List<String> lines = Arrays.stream(cleaned.split("\\R", -1)).toList();
        String sanitized = lines.stream()
                .limit(3)
                .filter(line -> !SKILL_IDENTITY_OVERRIDE_PATTERN.matcher(line.trim()).matches())
                .collect(Collectors.joining("\n"));
        if (lines.size() > 3) {
            String tail = lines.subList(3, lines.size()).stream().collect(Collectors.joining("\n"));
            sanitized = sanitized.isEmpty() ? tail : sanitized + "\n" + tail;
        }
        return sanitized.trim();
    }

    private Model createModel(RuntimeRoutingConfig config) {
        String provider = normalize(config.provider());
        GenerateOptions options = GenerateOptions.builder()
                .temperature(config.temperature())
                .maxTokens(config.maxTokens())
                .stream(modelStreamingEnabled)
                .build();
        logger.info(
                "runtime_model_create provider={} model={} modelStreamingEnabled={} optionsStream={}",
                provider,
                config.model(),
                modelStreamingEnabled,
                options.getStream());
        if ("openai".equals(provider) || "einwin".equals(provider)) {
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .modelName(config.model())
                    .stream(modelStreamingEnabled)
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
                    .stream(modelStreamingEnabled)
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

# Feishu Agent App 对标 openclaw-lark 改造设计

日期：2026-05-27

## 1. 背景

当前 `feishu-agent-app` 已经具备 Spring Boot 独立部署、飞书 WebSocket/HTTP 入口、RuntimeOrchestrator、AgentScope HarnessAgent、RedisSession、Skill Catalog、Skill Binding、飞书用户信息缓存、限流、去重、用量记录等基础能力。

用户反馈的主要问题集中在两类：

1. Skill 加载和执行存在问题。
2. 飞书各类消息和上下文处理不够完善，需要对标官方 `openclaw-lark` 方案。

本次改造原则：

- 复用现有 `feishu-agent-app`，不推倒重做。
- 不直接移植 openclaw-lark 的 TypeScript runtime。
- 不修改 `agentscope-core`、`agentscope-harness` 等上游核心模块，除非后续明确确认必须修改。
- 将 openclaw-lark 作为飞书通道架构参考，按 Java/Spring Boot/AgentScope Harness 的方式落地。
- 第一批改造聚焦可验证闭环，避免一次性引入过多能力。

本地对标参考目录：

- `.reference/openclaw-lark`

关键参考文件：

- `.reference/openclaw-lark/src/channel/plugin.ts`
- `.reference/openclaw-lark/src/messaging/inbound/parse.ts`
- `.reference/openclaw-lark/src/messaging/inbound/gate.ts`
- `.reference/openclaw-lark/src/messaging/inbound/mention.ts`
- `.reference/openclaw-lark/src/messaging/types.ts`
- `.reference/openclaw-lark/openclaw.plugin.json`
- `.reference/openclaw-lark/skills/feishu-channel-rules/SKILL.md`

## 2. 当前问题判断

### 2.1 Skill 加载和执行问题

当前 `RuntimeModelInvoker` 构建 `HarnessAgent` 时，如果配置了 skill repo，就直接加载整个 `FileSystemSkillRepository`。

现状问题：

- `allowedSkills` 主要被拼入 prompt，没有成为实际加载边界。
- `requestedSkill` 只是提示模型优先使用，不是强制选择。
- `DefaultSkillAuthorizationService` 只判断 skill 是否存在于 catalog，缺少 agent/user/部门/角色/群维度授权。
- `FileSystemSkillCatalogService` 只扫描目录和 `SKILL.md`，不读取 metadata，不支持 `alwaysActive`。
- Skill 与 Tool 权限没有清晰分层。

风险：

- Agent 可能看到未授权 skill。
- 请求指定 skill 时，只是软约束。
- 公共 skill、个人 skill、通道规则 skill、企业治理规则 skill 不易区分。

### 2.2 飞书消息处理问题

当前飞书事件处理链路大致为：

```text
Feishu SDK / HTTP callback
  -> FeishuEventRequest
  -> FeishuEventService
  -> SendMessageRequest
  -> RuntimeOrchestratorService
```

现状问题：

- `FeishuEventRequest` 只保留 text/chatId/chatType/userOpenId 等扁平字段。
- mention 缺少 key/isBot/isAll 等信息。
- 未完整建模 rootId/parentId/threadId。
- 未完整建模 senderIsBot、rawMessage、rawSender。
- 群聊缺少 requireMention gate。
- DM 缺少 open/allowlist/pairing 策略。
- bot sender 默认处理策略不清晰，可能出现机器人互相触发。
- `FeishuWebSocketEventClient` 同时承担 SDK 入口、事件转换、身份解析、mention 解析、reply、reaction，职责过重。

风险：

- 群消息可能误触发 Agent。
- 飞书富消息、文件、图片、interactive message 后续难以扩展。
- 企业身份和飞书身份没有在 runtime 上下文中稳定建模。

## 3. 目标架构

本次目标不是重写应用，而是在现有项目中加入两条清晰边界：

1. 飞书通道标准化边界。
2. Skill 运行时授权边界。

目标链路：

```text
Feishu SDK / HTTP callback
  -> FeishuRawEventMapper
  -> FeishuMessageContext
  -> FeishuMessageGate
  -> FeishuIdentityResolver
  -> FeishuConversationRouter
  -> RuntimeOrchestratorService
  -> RuntimeSkillPlanResolver
  -> AuthorizedSkillRepository + Toolkit
  -> HarnessAgent
  -> FeishuReplyService
```

第一批不会一次性实现全部节点，但新增类型和接口时会按这个目标方向设计。

## 4. 本次开发范围

本次开发采用最小闭环方案，优先解决核心问题。

### 4.1 Skill 强授权加载

新增或调整：

- `RuntimeSkillPlan`
- `RuntimeSkillPlanResolver`
- `AuthorizedSkillRepository`
- `SkillMetadata`
- `SkillMetadataCatalog` 或增强现有 `SkillCatalogService`
- `DefaultSkillAuthorizationService`
- `RuntimeModelService` / `RuntimeModelInvoker`
- `DefaultRuntimeOrchestratorService`

目标行为：

- 每次 runtime 调用前先计算 `RuntimeSkillPlan`。
- `alwaysActive` skill 总是加载。
- agent 发布绑定的 enabled skill 才进入可加载集合。
- `requestedSkill` 必须在 allowed skill 中才有效。
- 未授权 skill 不进入 `AuthorizedSkillRepository`。
- prompt 中的 skill 信息只作为模型行为提示，不作为安全边界。

第一批授权来源：

- `AgentSkillBindingRepository.listPublishedEnabledSkillNames(agentId)`
- filesystem catalog 存在性校验
- `alwaysActive` metadata

暂不做：

- 个人 skill 授权。
- 部门/角色授权。
- 群级 skill 授权。
- skill version lock。
- tool contract 强绑定。

### 4.2 飞书 MessageContext 与 Gate

新增或调整：

- `FeishuMessageContext`
- `FeishuMention`
- `FeishuResource`
- `FeishuInboundMessageMapper`
- `FeishuMessageGate`
- `GateResult`
- `FeishuWebSocketEventClient`
- `FeishuWebSocketPayloadMapper`
- `FeishuEventService`

目标行为：

- WebSocket 事件先转成标准 `FeishuMessageContext`。
- 保留 messageId/chatId/chatType/content/contentType/mentions/rootId/parentId/threadId/senderIsBot 等字段。
- mention 支持 key/openId/userId/unionId/name/bot/all。
- 群聊默认必须 @bot 才触发 Agent。
- @all 默认不触发 Agent。
- bot sender 默认不触发 Agent。
- gate 拒绝的消息不调用 Runtime。
- 现有 `FeishuEventRequest` 继续保留，避免 HTTP callback 行为被直接打断。

第一批不做完整媒体能力：

- image/file/audio/video 先进入 `resources` 模型，默认可为空。
- 后续再补下载和资源 Tool。

### 4.3 RedisSession 生产强校验

调整：

- `RuntimeSessionConfig`

目标行为：

- dev/local 环境仍允许 fallback 到 `JsonSession`。
- 如果配置 `feishu.session.redis.required=true`，且未配置 Redis URL，则启动失败。
- 后续生产 profile 可开启该配置，避免无状态部署误用本地 session。

## 5. 后续开发范围

以下内容不进入本次第一批开发，但需要保留扩展方向。

### 5.1 企业身份与数据接口 Tool

目标：

- 飞书 openId/userId/unionId 解析为企业 internalUserId/mobile/employeeNo。
- RuntimeContext 中稳定传递企业身份。
- 企业数据接口封装为受控 Tool。

后续候选 Tool：

- `enterprise_get_user_profile`
- `enterprise_query_user_data`
- `enterprise_query_project_metrics`
- `enterprise_query_business_data`
- `enterprise_analyze_business_context`

原则：

- 不让模型直接拼企业接口 URL。
- 不把 token 暴露给 prompt。
- 敏感查询需要审计。
- 高风险写操作需要确认。

### 5.2 Tool Contract 与 Skill 分层

对标 openclaw-lark 的 `openclaw.plugin.json`：

- Skill 负责任务说明、流程、输出格式、使用指引。
- Tool 负责真实可执行能力。
- Contract 负责声明 skill 可使用哪些工具。

后续目标：

- Skill metadata 支持 contracts/tools。
- RuntimeSkillPlan 决定注入哪些 Tool。
- Tool 调用前后记录审计。
- 敏感 Tool 加确认机制。

### 5.3 飞书回复体验增强

后续增强：

- interactive card。
- streaming card update。
- status card。
- tool call trace card。
- reply in thread 策略细化。
- 长任务先发“处理中”，完成后更新。

### 5.4 飞书富消息和资源处理

后续增强：

- text/post/interactive/image/file/audio/video/merge_forward 等消息类型转换。
- 文件下载工具。
- 图片 OCR 或模型输入。
- 文档/多维表格/日历/任务等飞书 OpenAPI Tool。

### 5.5 多账号、pairing、群配置

后续增强：

- 多 Feishu app/account。
- DM pairing。
- group allowlist。
- per-group requireMention。
- per-group tool policy。
- onboarding。
- directory list peers/groups。

## 6. 本次改造文件影响范围

预计主要修改：

- `feishu-agent-app/src/main/java/com/company/feishuagent/runtime/service/RuntimeModelInvoker.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/runtime/service/RuntimeModelService.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/runtime/service/DefaultRuntimeOrchestratorService.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/runtime/auth/DefaultSkillAuthorizationService.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/runtime/skill/FileSystemSkillCatalogService.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/runtime/config/RuntimeSessionConfig.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/feishu/service/FeishuWebSocketEventClient.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/feishu/service/FeishuWebSocketPayloadMapper.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/feishu/service/FeishuEventService.java`
- `feishu-agent-app/src/main/java/com/company/feishuagent/feishu/api/FeishuEventRequest.java`

预计新增包：

- `com.company.feishuagent.runtime.skill`
- `com.company.feishuagent.feishu.model`
- `com.company.feishuagent.feishu.service` 下若干服务类

预计新增测试：

- `RuntimeSkillPlanResolverTest`
- `AuthorizedSkillRepositoryTest`
- `DefaultSkillAuthorizationServiceTest` 增强
- `FileSystemSkillCatalogServiceTest` 增强
- `FeishuMessageGateTest`
- `FeishuInboundMessageMapperTest`
- `FeishuEventServiceTest` 增强
- `RuntimeSessionConfigTest` 或相关配置行为测试

## 7. 验证方式

优先执行模块级测试：

```bash
mvn -pl feishu-agent-app test
```

如果模块依赖需要同时构建：

```bash
mvn -pl feishu-agent-app -am test
```

格式检查：

```bash
mvn spotless:check
```

如有格式问题：

```bash
mvn spotless:apply
```

## 8. 风险与边界

### 8.1 风险

- `feishu-agent-app` 当前是 untracked 目录，需要避免误覆盖已有工作。
- Skill repository 接口能力需要以当前 AgentScope 实际 API 为准，不能凭空假设方法签名。
- WebSocket SDK 事件对象字段需要以当前 Lark Java SDK 实际模型为准。
- 不能把 prompt 规则当成权限边界。

### 8.2 边界

本次不做：

- 修改 AgentScope core。
- 修改 RedisSession core。
- 引入新外部依赖，除非已有依赖无法完成 front matter 解析且经过确认。
- 实现完整 openclaw-lark plugin runtime。
- 实现完整飞书 OpenAPI tool family。
- 实现企业数据接口真实调用。

## 9. 实施顺序

推荐实施顺序：

1. Skill metadata/catalog 增强。
2. Skill authorization 使用 published binding。
3. RuntimeSkillPlanResolver。
4. AuthorizedSkillRepository。
5. RuntimeModelInvoker 接入 RuntimeSkillPlan。
6. 飞书 MessageContext 模型。
7. FeishuInboundMessageMapper。
8. FeishuMessageGate。
9. FeishuEventService 接入 gate。
10. RuntimeSessionConfig 增加 required Redis 配置。
11. 补测试。
12. 跑模块测试和格式检查。

## 10. 成功标准

本次改造完成后应满足：

- 未授权 skill 不会被加载到 HarnessAgent。
- requestedSkill 未授权时不会被执行或作为可用 skill 暴露。
- alwaysActive skill 可被识别并自动进入加载计划。
- 群聊默认必须 @bot 才会触发 runtime。
- bot sender 默认不会触发 runtime。
- @all 默认不会触发 runtime。
- 飞书消息上下文在 runtime 前被标准化。
- 生产可配置强制 RedisSession，避免误用本地 JsonSession。
- `mvn -pl feishu-agent-app test` 或明确的替代验证命令通过。

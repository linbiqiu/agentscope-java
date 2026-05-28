# 飞书企业智能体服务 - 技术实现方案（MVP）

日期：2026-05-24  
状态：Updated

## 1. 实现目标

在独立应用 `feishu-agent-app` 中实现以下最小可用能力：
- 飞书事件接入（WebSocket 默认，HTTP 兼容）。
- 员工只能使用，不能安装/调整技能。
- 运营通过草稿/发布控制 agent 可用技能。
- 技能仓库基于指定目录，运行时自动识别变更并热加载。
- 技能变更不依赖应用重新发版。

## 2. 当前架构（简化后）

核心层次：
1. `api-gateway`：飞书事件接入与消息回发。
2. `runtime-orchestrator`：会话路由与技能授权。
3. `control-plane`：运营草稿/发布配置。
4. `skill-catalog`：文件夹技能仓库扫描与缓存刷新。
5. `observability-audit`：调用日志与审计。

已移除：
- 技能包同步服务（sync module）。
- 版本化发布/业务回滚链路。

## 3. 技能仓库变更识别与运行时可见性

### 3.1 目录规范
- 通过 `FEISHU_RUNTIME_SKILL_REPO_DIR` 指定仓库目录。
- 每个技能作为子目录，目录内需存在 `SKILL.md`。

### 3.2 识别机制
- 运行时基于 `FileSystemSkillRepository` 读取技能仓库。
- 在消息处理路径会获取当前可见技能列表（`getAllSkillNames()`），并用于本次请求编排。
- 因此仓库目录的新增/删除技能会在后续请求中自动可见或失效。

### 3.3 生效行为
- 新增技能目录：后续请求可自动识别。
- 删除技能目录：后续请求自动不再可用。
- 全程无需重新打包或重启应用。

## 4. 绑定、授权与原生编排实现

### 4.1 运营配置
- 草稿保存：`PUT /api/admin/agents/{agentId}/bindings/draft`
- 发布生效：`POST /api/admin/agents/{agentId}/bindings/publish`

### 4.2 草稿校验
- `DefaultAgentBindingAdminService` 保存草稿前校验 skill 是否存在于当前仓库缓存。
- 未上传到仓库的 skill 名将被拒绝。

### 4.3 运行时授权
- `DefaultSkillAuthorizationService` 按交集放行：
  - agent 已发布且启用的 skill
  - 当前仓库已加载技能
- 非交集 skill 一律拒绝。

## 5. 配置与部署

测试/生产统一 env 注入：
- `FEISHU_RUNTIME_SKILL_REPO_DIR`
- `FEISHU_RUNTIME_SKILL_REPO_REFRESH_INTERVAL_MS`

当前 compose 映射建议：
- 宿主机技能目录挂载到容器 `/data/skill-repo`（只读）。

## 6. 身份解析、档案与上下文透传（新增）

### 6.1 事件入口身份解析
- WebSocket 入口解析发言人（actor）与被 @ 人（mentions）。
- mentions 采用双来源合并：
  1) 事件结构化 `mentions[]`
  2) 消息内容中的 `<at ...>...</at>` 标签
- 两路结果按身份主键去重后写入 `identityContextJson`。

### 6.2 用户档案读取与回源
- 用户档案仓储支持按 `openId/unionId/userId` 查询。
- 运行时身份解析顺序：
  1) DB-first：`findByOpenId` -> `findByUnionId` -> `findByUserId`
  2) miss 时调用飞书 contact 接口回源
- 回源结果继续 upsert 到档案表，实现去重与增量补齐。

### 6.3 持久化准入规则
- 用户档案写入前进行完整性校验。
- 仅当 `openId/mobile/employeeNo/email` 全部存在时允许写入。
- 不完整身份仅用于当次会话透传，不落库。

### 6.4 标识语义约束
- 事件主标识 `userOpenId` 严格为 openId，不回退 unionId/userId。
- 其它身份字段仅通过 `actor/mentions` 与 identity context 提供。

### 6.5 runtime / skill / tool / MCP 透传
- `identityContextJson` 在 runtime 调用链保留原始字符串键：
  - `identityContextJson`
  - `identity_context_json`
- 同时注入结构化上下文键：
  - `identity`
  - `identity_actor`
  - `identity_mentions`
  - `identity_conversation`
- skill/tool/MCP 通过上述键直接消费身份信息，无需重复解析。

## 7. 数据模型（当前保留）

当前核心表：
- `agent_profile`
- `agent_skill_binding`
- `publish_record`
- `conversation_log`
- `tool_call_log`
- `sensitive_access_audit`
- `runtime_call_usage_log`
- `runtime_call_usage_hourly`
- `feishu_user_profile`

已移除：
- `sync_record` 及相关同步模型。

## 8. 后续演进（非当前阶段）

后续如需运营可视化管理，可在当前能力上平滑增加：
- 技能仓库管理页面（上传/禁用/标签）。
- 审批流与发布审计增强。
- 更细粒度的技能级策略控制。

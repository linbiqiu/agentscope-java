# 飞书企业智能体服务 MVP 文档总索引

日期：2026-05-24  
状态：Updated

## 文档拆分说明

MVP 文档按“需求 / 设计 / 技术实现 / 部署运维”拆分维护。

### 1) 需求文档
- `docs/superpowers/specs/2026-05-22-feishu-enterprise-agent-mvp-requirements.md`

### 2) 设计文档
- `docs/superpowers/specs/2026-05-22-feishu-enterprise-agent-mvp-design.md`

### 3) 技术实现方案
- `docs/superpowers/specs/2026-05-22-feishu-enterprise-agent-mvp-technical-design.md`

### 4) 部署与运维
- `docs/deployment/feishu-agent/overview.md`
- `docs/deployment/feishu-agent/testing-deployment-runbook.md`
- `docs/deployment/feishu-agent/production-deployment-runbook.md`
- `docs/deployment/feishu-agent/operations-commands.md`
- `docs/deployment/feishu-agent/persistence-and-backup.md`

### 5) 环境编排文件
- 测试环境：`feishu-agent-app/deploy/test/docker-compose.yml`
- 生产环境：`feishu-agent-app/deploy/prod/docker-compose.yml`

当前基线说明：
- 已移除版本化发布/回滚链路。
- 已移除技能上传下载与同步管理模块。
- 当前使用“文件夹技能仓库 + 自动热加载 + agent 绑定授权”最小闭环。
- 新增“身份上下文透传 + 用户档案沉淀”链路：
  - 发言人/被@人身份统一进入 `identityContextJson`
  - `userOpenId` 语义收敛为 openId
  - 用户档案采用 DB-first + 回源补齐
  - 不完整用户信息不入库
  - skill/tool/MCP 可直接消费结构化身份上下文

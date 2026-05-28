# Feishu Agent 部署方案总览

## 目标
- 独立部署 `feishu-agent-app`，低侵入复用 AgentScope 能力。
- 应用层无状态，多副本横向扩展。
- 关键数据与技能资源必须持久化，重启不丢失。

## 系统分层
1. `api-gateway`：飞书事件接入、验签、幂等处理、入口路由。
2. `runtime-orchestrator`：会话编排、权限校验、智能体调用、记忆注入策略。
3. `control-plane`：运营后台、RBAC、草稿发布流程、配置管理。
4. `skill-registry-sync`：技能包元数据管理、发布后同步、删除与回滚。
5. `observability`：日志、审计、指标、告警。

## 运行依赖
- PostgreSQL：配置、发布、审计、幂等元数据。
- Redis：会话、限流、短期状态。
- 技能资源目录：运行机本地持久目录（如 `/data/agent-skills`）。
- AI Router：支持 OpenAI/Anthropic 协议；当前阶段使用环境变量本地配置，后续演进为事件+缓存+数据库配置读取。

## 请求链路
默认：飞书 WebSocket 事件流 → `feishu-websocket-client` → `runtime-orchestrator` → AgentScope Runtime → skill 执行 → 响应回飞书。

兼容：飞书 HTTP 回调 → `api-gateway` → `runtime-orchestrator` → AgentScope Runtime → skill 执行 → 响应回飞书。

## 环境配置索引
- 测试环境 compose：`feishu-agent-app/deploy/test/docker-compose.yml`
- 生产环境 compose：`feishu-agent-app/deploy/prod/docker-compose.yml`
- 持久化与备份基线：`docs/deployment/feishu-agent/persistence-and-backup.md`
- 容量规划：`docs/deployment/feishu-agent/capacity-planning.md`
- 测试环境部署实施手册：`docs/deployment/feishu-agent/testing-deployment-runbook.md`
- 生产环境部署实施手册：`docs/deployment/feishu-agent/production-deployment-runbook.md`
- 常见运维命令手册：`docs/deployment/feishu-agent/operations-commands.md`

# Feishu Agent 测试环境部署实施手册

## 1. 目标

本手册用于在测试环境完成 `feishu-agent-app` 的可重复部署与验证，重点验证：
- 持久化不丢数据（PostgreSQL / Redis / skills 目录）
- WebSocket 默认链路可用
- 会话、限流、幂等、技能加载可用

## 2. 前置条件

- 一台 Linux 或 macOS 测试机器（建议 4 vCPU / 8 GB 内存 / 50 GB 磁盘）
- 已安装 Docker 与 Docker Compose
- 已准备应用镜像：`feishu-agent-app:latest`
- 已准备飞书测试应用参数（如启用 WebSocket）

目录约定（示例）：
- 项目目录：`/opt/feishu-agent`
- compose 文件：`feishu-agent-app/deploy/test/docker-compose.yml`

## 3. 配置检查

部署前确认 `docker-compose.yml` 中以下关键项：
- PostgreSQL 数据目录挂载：`pg_data:/var/lib/postgresql/data`
- Redis 数据目录挂载：`redis_data:/data`
- 应用技能目录挂载：`skill_data:/data/agent-skills`
- Redis AOF 开启：`--appendonly yes`
- 应用环境变量：
  - `FEISHU_RATE_LIMIT_BACKEND=redis`
  - `FEISHU_EVENT_DEDUP_BACKEND=jdbc`
  - `FEISHU_RUNTIME_ROUTING_BACKEND=jdbc`
  - `FEISHU_SESSION_REDIS_ENABLED=true`
  - `FEISHU_RUNTIME_SKILL_SYNC_TARGET_DIR=/data/agent-skills`
  - `FEISHU_WEBSOCKET_APP_ID` / `FEISHU_WEBSOCKET_APP_SECRET` 必填
  - `FEISHU_WEBSOCKET_URL` 可选（默认由 SDK/客户端处理）

## 4. 部署步骤

1) 拉起基础服务与应用
- 在仓库根目录执行：
  - `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml up -d`

2) 检查容器状态
- `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml ps`
- 期望：postgres、redis、feishu-agent-app 均为 `healthy`/`running`

3) 检查应用日志
- `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml logs -f feishu-agent-app`
- 期望：无启动失败；数据库连接、Redis 连接成功

## 5. 功能验证步骤（必须执行）

### 5.1 幂等验证
- 发送同一个 `event_id` 两次
- 期望：第一次正常处理，第二次命中幂等（不重复执行业务）

### 5.2 限流验证
- 同一用户在 1 分钟内连续触发超过阈值
- 期望：触发限流响应（rate limit exceeded）

### 5.3 会话持续性验证
- 发起一次对话，记录上下文行为
- 重启应用容器后继续同会话
- 期望：会话状态仍可继续（Redis 会话有效）

### 5.4 技能目录持久化验证
- 在技能目录生成/同步测试 skill 文件
- 重启应用容器
- 期望：重启后技能仍可扫描加载

## 6. 持久化验证（重启场景）

1) 重启应用容器
- `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml restart feishu-agent-app`
- 验证：应用配置、会话、技能加载正常

2) 重启 Redis 容器
- `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml restart redis`
- 验证：AOF 恢复后会话/限流行为符合预期

3) 重启 PostgreSQL 容器
- `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml restart postgres`
- 验证：运营配置、幂等记录可用

## 7. 版本更新流程（测试环境）

1) 拉取新镜像
- `docker pull feishu-agent-app:<new-tag>`

2) 更新 compose 中应用镜像 tag

3) 滚动重建
- `docker compose -f feishu-agent-app/deploy/test/docker-compose.yml up -d`

4) 执行第 5 章回归验证

## 8. 回滚流程（测试环境）

1) 将镜像 tag 改回上一版本
2) 重新执行 `up -d`
3) 验证核心链路：
- WebSocket 入站
- 授权 skill 调用
- 会话持续
- 幂等/限流

## 9. 常见问题排查

- 应用启动失败：优先看容器日志与 DB/Redis 连接串
- 技能未加载：检查 `/data/agent-skills` 是否有内容与挂载是否成功
- 会话丢失：检查 Redis AOF 和挂载目录
- 限流不生效：检查 `FEISHU_RATE_LIMIT_BACKEND` 是否为 `redis`

## 10. 验收记录模板（建议）

每次部署后记录：
- 部署时间
- 镜像版本
- 幂等验证结果
- 限流验证结果
- 会话持续验证结果
- 技能重启加载结果
- 结论（通过/不通过）

# Feishu Agent 生产环境部署实施手册

## 1. 目标

本手册用于生产环境标准化部署 `feishu-agent-app`，确保：
- 应用层无状态，多副本可扩展
- 数据与技能资源持久化
- 可灰度、可回滚、可审计

## 2. 生产基础要求

推荐最小生产配置（起步）：
- App：2~3 副本，每副本 2 vCPU / 4~8 GB
- PostgreSQL：主备或托管高可用，独立 SSD
- Redis：主从或托管高可用，AOF 打开
- 持久化目录：
  - `/data/feishu-agent/postgres`
  - `/data/feishu-agent/redis`
  - `/data/feishu-agent/skills`

## 3. 配置文件与密钥

生产 compose：`feishu-agent-app/deploy/prod/docker-compose.yml`

必须通过环境变量注入：
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `FEISHU_AGENT_APP_IMAGE`
- `FEISHU_WEBSOCKET_APP_ID`
- `FEISHU_WEBSOCKET_APP_SECRET`
- `AI_ROUTER_BASE_URL`
- `AI_ROUTER_API_KEY`
- `AI_ROUTER_PROVIDER`
- `AI_ROUTER_MODEL`

可选环境变量：
- `FEISHU_WEBSOCKET_URL`（默认由 SDK/客户端处理，无需手工填写）

要求：
- 禁止明文写入仓库
- 使用 CI/CD Secret 或密钥管理系统注入
- 当前阶段模型路由以环境变量为准，后续升级为事件+缓存+数据库读取

## 4. 首次部署步骤

1) 准备生产目录
- 确保宿主机存在并授权：
  - `/data/feishu-agent/postgres`
  - `/data/feishu-agent/redis`
  - `/data/feishu-agent/skills`

2) 下发环境变量文件（不入库）
- 例如：`.env.prod`

3) 启动服务
- `docker compose --env-file .env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml up -d`

4) 检查服务健康
- `docker compose --env-file .env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml ps`

5) 检查应用日志
- `docker compose --env-file .env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml logs -f feishu-agent-app`

## 5. 上线前验证清单（必须）

- WebSocket 事件可正常接入
- 员工仅能调用授权 skill
- 未授权 skill 请求返回可用列表
- 会话连续对话正常
- 限流生效
- 幂等生效
- 技能目录加载正常
- traceId 链路可追踪

## 6. 灰度发布流程

1) 先在小范围用户或单组织灰度
2) 观察 30~60 分钟核心指标
3) 指标稳定后逐步全量

核心观察指标：
- App：错误率、延迟、重启次数
- Redis：内存、延迟、evicted_keys
- PostgreSQL：连接、慢查询、锁等待
- 业务：限流命中率、幂等命中率、会话失败率

## 7. 版本升级流程（生产）

1) 预检查
- 备份 PostgreSQL
- 确认 Redis AOF 正常
- 确认技能目录可读写

2) 发布新镜像
- 更新 `FEISHU_AGENT_APP_IMAGE`
- 执行 `up -d`

3) 发布后回归
- 执行第 5 章验证清单
- 重点验证：技能重载、会话持续、授权边界

## 8. 回滚流程（生产）

触发条件（任一满足）：
- 5xx 持续升高
- 核心接口不可用
- WebSocket 事件处理明显异常
- 会话/技能加载异常影响主流程

回滚步骤：
1) 镜像回退到上一稳定版本
2) `up -d` 重新拉起
3) 验证核心链路恢复
4) 记录回滚事件与根因分析

## 9. 灾备与恢复

- PostgreSQL：每日全量 + WAL/PITR
- Redis：AOF + RDB
- skills 目录：每日快照备份（或对象存储镜像）

恢复演练建议：
- 每月一次恢复演练
- 至少覆盖 DB 恢复、Redis 恢复、skills 目录恢复

## 10. 运维SOP（例行）

每日：
- 检查错误率、延迟、资源占用、告警

每周：
- 检查备份有效性
- 抽样检查审计日志完整性

每月：
- 执行一次容灾恢复演练
- 复盘容量趋势并评估扩容

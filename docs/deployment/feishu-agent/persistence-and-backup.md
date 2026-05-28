# Feishu Agent 数据持久化与备份基线

## 强制约束
- 禁止把关键状态只放在容器可写层。
- PostgreSQL、Redis、skill 文件目录都必须绑定持久卷或宿主机目录。
- 应用重启、Pod 漂移后数据必须保留且可恢复。

## PostgreSQL
- 数据目录持久化（例如 `/var/lib/postgresql/data`）。
- 每日全量备份 + WAL/PITR。
- 建议保留 7~30 天恢复窗口。

## Redis
- 开启 AOF（建议 `appendfsync everysec`）。
- 建议保留 RDB 快照用于快速恢复。
- 数据目录持久化（例如 `/data`）。

## Skill 文件与资源
- 同步目录配置为持久化路径：`/data/agent-skills`。
- 发布流程使用：临时目录下载 → 校验 hash/manifest → 原子 rename。
- 删除/回滚先写审计记录，再执行目录变更。

## 编排建议
- Docker Compose：使用 named volume 或 bind mount。
- Kubernetes：使用 PVC（Redis/DB/skills），数据库与缓存建议托管或 Stateful 方案。

## 验证清单
1. 重启应用后，已发布技能仍能加载并可调用。
2. 重启 Redis 后，会话与限流策略符合预期（AOF 生效）。
3. 重启 PostgreSQL 后，运营配置与审计记录仍在。
4. 节点迁移后，skill 目录可被启动加载器扫描。

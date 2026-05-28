# Feishu Agent 容量与扩展规划

## 目标规模
- 约 500 项目、3000 员工用户。
- 初期试点低流量，逐步扩展。

## 分阶段建议
### 阶段 A（试点）
- App：2 副本，每副本 2 vCPU / 4 GB
- Redis：2 vCPU / 4 GB
- PostgreSQL：2 vCPU / 8 GB / SSD 200 GB

### 阶段 B（稳态）
- App：3~4 副本，每副本 2~4 vCPU / 8 GB
- Redis：4 vCPU / 8 GB（主从）
- PostgreSQL：4 vCPU / 16 GB / SSD 500 GB

### 阶段 C（目标规模）
- App：6~8 副本，每副本 4 vCPU / 8~16 GB
- Redis：8 vCPU / 16 GB（主从或分片）
- PostgreSQL：8 vCPU / 32 GB / SSD 1 TB（可选读副本）

## 关键监控
- App：p95/p99 延迟、5xx、实例重启次数。
- Redis：used_memory、evicted_keys、latency、ops/sec。
- PostgreSQL：连接数、慢查询、锁等待、复制延迟。
- 业务：幂等命中率、限流命中率、会话加载失败率。

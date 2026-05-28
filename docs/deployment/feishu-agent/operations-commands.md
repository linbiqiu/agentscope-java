# Feishu Agent 常见运维命令手册

## 1. 测试环境命令

> 基于：`feishu-agent-app/deploy/test/docker-compose.yml`  
> 环境文件：`feishu-agent-app/deploy/test/.env.test.example`

### 1.1 启动
```bash
docker compose --env-file feishu-agent-app/deploy/test/.env.test.example -f feishu-agent-app/deploy/test/docker-compose.yml up -d
```

### 1.2 查看状态
```bash
docker compose --env-file feishu-agent-app/deploy/test/.env.test.example -f feishu-agent-app/deploy/test/docker-compose.yml ps
```

### 1.3 查看日志
```bash
docker compose --env-file feishu-agent-app/deploy/test/.env.test.example -f feishu-agent-app/deploy/test/docker-compose.yml logs -f feishu-agent-app
```

### 1.4 重启应用
```bash
docker compose --env-file feishu-agent-app/deploy/test/.env.test.example -f feishu-agent-app/deploy/test/docker-compose.yml restart feishu-agent-app
```

### 1.5 停止并保留数据
```bash
docker compose --env-file feishu-agent-app/deploy/test/.env.test.example -f feishu-agent-app/deploy/test/docker-compose.yml down
```

### 1.6 停止并删除卷（仅测试清理）
```bash
docker compose --env-file feishu-agent-app/deploy/test/.env.test.example -f feishu-agent-app/deploy/test/docker-compose.yml down -v
```

## 2. 生产环境命令

> 基于：`feishu-agent-app/deploy/prod/docker-compose.yml`  
> 环境文件：`feishu-agent-app/deploy/prod/.env.prod`（由 `.env.prod.example` 复制）

### 2.1 首次启动/更新启动
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml up -d
```

### 2.2 查看状态
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml ps
```

### 2.3 查看应用日志
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml logs -f feishu-agent-app
```

### 2.4 重启单服务
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml restart feishu-agent-app
```

### 2.5 平滑重建应用（不动数据卷）
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml up -d --no-deps feishu-agent-app
```

### 2.6 停止服务（保留数据）
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml down
```

## 3. 升级与回滚

### 3.1 升级
1. 修改 `.env` 中 `FEISHU_AGENT_APP_IMAGE` 为新版本。
2. 执行：
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml up -d
```
3. 执行健康检查和业务回归。

### 3.2 回滚
1. 将 `FEISHU_AGENT_APP_IMAGE` 改回上一稳定版本。
2. 执行同样 `up -d`。
3. 复测核心链路：WebSocket、会话、幂等、限流、技能加载。

## 4. 数据与持久化检查

### 4.1 检查 PostgreSQL 数据目录挂载
```bash
docker inspect feishu-postgres-prod --format '{{json .Mounts}}'
```

### 4.2 检查 Redis AOF
```bash
docker exec -it feishu-redis-prod redis-cli CONFIG GET appendonly
```

### 4.3 检查技能目录挂载
```bash
docker inspect feishu-agent-app-prod --format '{{json .Mounts}}'
```

## 5. 健康与排障

### 5.1 快速看最近日志
```bash
docker compose --env-file feishu-agent-app/deploy/prod/.env.prod -f feishu-agent-app/deploy/prod/docker-compose.yml logs --tail=200 feishu-agent-app
```

### 5.2 查看容器重启次数
```bash
docker ps --format 'table {{.Names}}\t{{.Status}}'
```

### 5.3 检查 Redis 连通性
```bash
docker exec -it feishu-redis-prod redis-cli ping
```

### 5.4 检查 PostgreSQL 连通性
```bash
docker exec -it feishu-postgres-prod pg_isready -U "$POSTGRES_USER" -d feishu_agent
```

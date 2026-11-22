# docker/ 本地基础设施

供本地联调启动 PostgreSQL 与 Redis（生产环境使用华为云 RDS / 分布式 Redis，不使用本目录）。

## 启动

```
cd docker
docker compose up -d
```

## 连接信息（仅本地）

| 服务 | 地址 | 账号 | 密码 |
| --- | --- | --- | --- |
| PostgreSQL | localhost:5432/nexus | nexus | nexus_local_dev |
| Redis | localhost:6379 | - | nexus_local_dev |

## 与后端 profile 对应

- `application-local.yml`：默认走 H2 内存库 + 弱依赖 Redis，无需 docker 也可启动。
- 连接真实 PG/Redis 验证连通性时，用 `NEXUS_PROFILE=postgres` 启动（见 nexus-backend/README 启动说明）。
- OBS 不提供本地模拟，连通性验证见 `docs/api-specification.md` 附录与 `application-prod.yml` 模板。
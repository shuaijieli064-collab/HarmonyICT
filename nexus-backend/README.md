# nexus-backend（成员 A）

NEXUS 后端服务：Java 21 + Spring Boot 3.3 + MyBatis-Plus + Spring Security(JWT)。
目标数据库 PostgreSQL（华为云 RDS），缓存 Redis（华为云 Redis），对象存储 OBS（阶段二接入）。

## 环境要求

- JDK 21+（本机 JDK 25 可用，编译目标 `--release 21`）
- Maven 3.9+（本机未装可解压到项目外目录，仓库内不含 Maven）

## 本地启动（默认 local profile，H2 内存库，无需任何外部服务）

```
mvn spring-boot:run
```

验证：

```
curl http://localhost:8080/actuator/health          # {"status":"UP"}
curl http://localhost:8080/api/v1/health            # 业务级健康检查
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
     -d '{"username":"demo","password":"passw0rd!","nickname":"演示"}'
```

## 连接真实 PG / Redis（postgres profile，docker 容器或华为云）

```
cd ../docker && docker compose up -d          # 启动本地 PG + Redis
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

指向华为云 RDS/Redis 时，用环境变量覆盖：`NEXUS_RDS_HOST/PORT/DB/USER/PASSWORD`、`NEXUS_REDIS_HOST/PORT/PASSWORD`（见 application-postgres.yml）。启动日志会输出 RDS/Redis 连通性检查结果（CloudConnectChecker）。

## OBS 连通性验证方式（阶段二接入 SDK 前的临时方式）

```
curl -I https://<bucket>.obs.<region>.myhuaweicloud.com
```

返回 403/200 均视为 endpoint 网络可达；阶段二接入 OBS SDK 后改用桶级鉴权探测。开通步骤与截图按赛题要求留存于 `docs/`（TODO(A, 阶段二): 补充华为云资源开通记录）。

## 目录边界（严格遵守 docs/00）

- A 独占本目录（除 `com/nexus/ai` 包实现）与 `docker/`、两份契约文档。
- `com/nexus/ai` 包内：A 只维护接口签名与 DTO，实现由成员 B 提供。
- 禁止触碰 `nexus-frontend/`。
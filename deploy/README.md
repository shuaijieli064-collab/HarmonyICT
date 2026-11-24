# NEXUS 华为云 ECS 部署说明

> 配套方案：`docs/cloud-deployment-plan.md`。本目录归云部署线独占，根目录 `README.md` 归前端交付线，勿混写。
> 前置：代金券到账、9 项云资源开通、凭据已填入 ECS `/etc/nexus/nexus.env`（权限 600，不入库）。

## 1. 目录内容

| 文件 | 用途 |
| --- | --- |
| `nexus-backend.service` | systemd unit：以 `prod` profile 启动后端 jar |
| `nexus-frontend.service` | systemd unit：`next start` 启动前端（:3000） |
| `nginx-nexus.conf` | nginx 站点：`/api` 反代 :8080，其余走 :3000，上传上限 20M |
| `nexus-deploy.sh` | 安装 / 上传 / 启停 / 状态 / 健康检查脚本 |

## 2. 首次部署步骤（在 ECS 上，Ubuntu/CentOS）

```bash
# 1) 安装运行时：Java 21、Node 20+、nginx；创建专用用户
sudo useradd -m -s /bin/bash nexus

# 2) 写入凭据（仅本机，禁止入库/回显）
sudo install -d -m 700 /etc/nexus
sudo vi /etc/nexus/nexus.env      # 见下方变量清单，chmod 600
sudo chmod 600 /etc/nexus/nexus.env

# 3) 构建产物（本地或 CodeArts）
#    后端：mvn -q clean package -pl nexus-backend -am
#    前端：cd nexus-frontend && NEXT_PUBLIC_API_BASE_URL=http://<EIP>:8080 npm ci && npm run build

# 4) 安装 unit + nginx，上传产物，启动
sudo ./deploy/nexus-deploy.sh install
sudo ./deploy/nexus-deploy.sh upload \
  nexus-backend/target/nexus-backend-0.1.0-SNAPSHOT.jar nexus-frontend
sudo ./deploy/nexus-deploy.sh start
sudo ./deploy/nexus-deploy.sh health
```

## 3. 凭据变量清单（`/etc/nexus/nexus.env`）

只列变量名，值由云控制台获取后填入本机文件：

- `NEXUS_JWT_SECRET`（≥32 字节随机串）
- `NEXUS_RDS_HOST/PORT/DB/USER/PASSWORD`
- `NEXUS_REDIS_HOST/PORT/PASSWORD`
- `NEXUS_OBS_ENDPOINT/BUCKET/AK/SK`
- `NEXUS_AI_MAAS_BASE_URL/API_KEY/MODEL/COMPLETION_PATH`
- `NEXUS_AI_OCR_IAM_ENDPOINT/ENDPOINT/USERNAME/PASSWORD/DOMAIN_NAME/PROJECT_ID`
- `NEXUS_CORS_ORIGINS`（公网 EIP 或域名，逗号分隔）

红线：禁止把任一值写入仓库、流水线文本、日志或对话；`git grep` 终检必须无明文密钥。

## 4. 首次建表（关键）

`application-prod.yml` 中 `spring.sql.init.mode=never`，空库启动会因无表失败。首次部署 RDS 后手工执行一次：

```bash
psql -h <RDS内网地址> -U <用户> -d nexus -f nexus-backend/src/main/resources/db/init-postgres.sql
```

## 5. 验收清单

- `curl http://<EIP>:8080/actuator/health` → `{"status":"UP"}`
- 启动日志 `journalctl -u nexus-backend -n 200` 出现 `[rds]/[redis]/[obs]` 连通性结果（OBS 无凭据时为 SKIPPED，生产应 OK）
- 上传 → OCR → MaaS → 落库 → 风险 → Agent 全链路公网可演示
- `sudo ./nexus-deploy.sh health` 后端与前端均正常

## 6. 回退

`upload` 会保留上一份 jar 为 `nexus-backend.jar.bak`：停服务 → 用备份覆盖当前 jar → 重启 → 健康检查。

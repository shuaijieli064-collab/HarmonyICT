# deploy/ — NEXUS 云上部署物料

对应 `docs/cloud-deployment-plan.md` 第 5 节改造 ④ 与第 6 节 CodeArts 流水线。目标拓扑：

```
公网 EIP
  ├─ :80  nginx ─→ Next.js (:3000)
  │        └─ /api/* 反代 ─→ Spring Boot (:8080)
  └─ :8080 直连后端（演示阶段规避 80 备案周期）
```

## 文件清单

| 文件 | 用途 |
| --- | --- |
| `nexus-backend.service` | systemd 单元：开机自启、崩溃拉起、从 `/etc/nexus/nexus.env` 注入环境变量 |
| `nginx-nexus.conf` | nginx 站点：前端 + API 反代 + 健康检查直通 |
| `nexus.sh` | 启停/状态/健康检查脚本（CodeArts 部署任务调用 `restart`、`health`） |

## ECS 首次安装步骤

```bash
# 1. 运行用户与目录
sudo useradd -r -s /usr/sbin/nologin nexus || true
sudo mkdir -p /opt/nexus /etc/nexus
sudo chown nexus:nexus /opt/nexus

# 2. 凭据环境文件（权限 600；凭据红线：只进此文件与 CodeArts 凭据管理，不进仓库）
sudo cp nexus.env.example /etc/nexus/nexus.env   # 填入真实值
sudo chmod 600 /etc/nexus/nexus.env

# 3. systemd 与 nginx
sudo cp nexus-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo cp nginx-nexus.conf /etc/nginx/sites-available/nexus.conf
sudo ln -sf /etc/nginx/sites-available/nexus.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

## CodeArts 部署任务动作序列

1. 上传包：构建产物 `nexus-backend.jar` → `/opt/nexus/nexus-backend.jar`（备份旧包）
2. 执行：`bash /opt/nexus/deploy/nexus.sh restart`
3. 健康新检查：`bash /opt/nexus/deploy/nexus.sh health`

## 验收对照（部署方案第 9 节）

- `/actuator/health` = UP，启动日志「云资源连通性 [rds]/[redis]/[obs]」均 OK
- 注册/登录/JWT 正常，`user_id` 隔离有效
- 上传真实资料 → OCR → MaaS 抽取 → 落库 → 时间线 → 风险证据 → Agent L0–L2
- 公网可访问、跨域正常（`NEXUS_CORS_ORIGINS` 已注入 EIP/域名）

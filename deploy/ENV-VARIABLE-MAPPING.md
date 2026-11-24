# NEXUS 环境变量对照表（云部署线核对产物）

> 核对时间：2026-11-24　依据：`docs/cloud-deployment-plan.md` 第 7 节、`application-prod.yml`、`nexus-ai/real-call.env.template`
> 用途：第九节分派的「等券期间准备」——确保本地真云验收与生产部署的变量一一对应，券到后按表填值不漏项。

## 1. 三套配置的职责边界（先分清再核对）

| 载体 | 用途 | 是否含密钥 |
| --- | --- | --- |
| `docs/cloud-deployment-plan.md` 第 7 节 | **全集清单**：本地验收 + 生产部署所有变量 | 否，只有变量名 |
| `nexus-ai/real-call.env.template` | **本地验收子集**：只跑 MaaS/OCR 真调用，不含 ECS/RDS/JWT | 模板占位符，真值只在本地 `real-call.env`（已 gitignore） |
| `application-prod.yml` | **生产部署全集**：ECS `/etc/nexus/nexus.env` 注入 | 占位符 `${VAR:}`，无明文 |
| 前端构建期 | `NEXT_PUBLIC_API_BASE_URL` 注入 `next build` | 否 |

**核对原则**：验收模板只需覆盖「真调用验收」所需变量；RDS/Redis/OBS/JWT/CORS 属部署环境，不应混进验收模板。

## 2. 逐项对照（方案第 7 节 25 个变量展开）

| # | 变量 | 方案第 7 节 | real-call.env.template | application-prod.yml | 结论 |
| --- | --- | :---: | :---: | :---: | --- |
| 1 | `NEXUS_JWT_SECRET` | ✅ | —（验收不需） | ✅ | 一致 |
| 2 | `NEXUS_RDS_HOST` | ✅ | — | ✅ | 一致 |
| 3 | `NEXUS_RDS_PORT` | ✅ | — | ✅ | 一致 |
| 4 | `NEXUS_RDS_DB` | ✅ | — | ✅ | 一致 |
| 5 | `NEXUS_RDS_USER` | ✅ | — | ✅ | 一致 |
| 6 | `NEXUS_RDS_PASSWORD` | ✅ | — | ✅ | 一致 |
| 7 | `NEXUS_REDIS_HOST` | ✅ | — | ✅ | 一致 |
| 8 | `NEXUS_REDIS_PORT` | ✅ | — | ✅ | 一致 |
| 9 | `NEXUS_REDIS_PASSWORD` | ✅ | — | ✅ | 一致 |
| 10 | `NEXUS_OBS_ENDPOINT` | ✅ | —（验收不传文件） | ✅ | 一致 |
| 11 | `NEXUS_OBS_BUCKET` | ✅ | — | ✅ | 一致 |
| 12 | `NEXUS_OBS_AK` | ✅ | — | ✅ | 一致 |
| 13 | `NEXUS_OBS_SK` | ✅ | — | ✅ | 一致 |
| 14 | `NEXUS_AI_MAAS_BASE_URL` | ✅ | ✅ | ✅ | 一致 |
| 15 | `NEXUS_AI_MAAS_API_KEY` | ✅ | ✅ | ✅ | 一致 |
| 16 | `NEXUS_AI_MAAS_MODEL` | ✅ | ✅ | ✅ | 一致 |
| 17 | `NEXUS_AI_MAAS_COMPLETION_PATH` | ✅ | ✅ | ✅ | 一致（本轮新增，v1/v2 切换） |
| 18 | `NEXUS_AI_OCR_IAM_ENDPOINT` | ✅ | ✅ | ✅ | 一致 |
| 19 | `NEXUS_AI_OCR_ENDPOINT` | ✅ | ✅ | ✅ | 一致 |
| 20 | `NEXUS_AI_OCR_USERNAME` | ✅ | ✅ | ✅ | 一致 |
| 21 | `NEXUS_AI_OCR_PASSWORD` | ✅ | ✅ | ✅ | 一致 |
| 22 | `NEXUS_AI_OCR_DOMAIN_NAME` | ✅ | ✅ | ✅ | 一致 |
| 23 | `NEXUS_AI_OCR_PROJECT_ID` | ✅ | ✅ | ✅ | 一致 |
| 24 | `NEXUS_CORS_ORIGINS` | ✅ | — | ✅ | 一致 |
| 25 | `NEXT_PUBLIC_API_BASE_URL` | ✅ | — | —（前端构建期） | 一致 |

## 3. 发现的两处差异与处置

| 差异项 | 现状 | 处置 |
| --- | --- | --- |
| `NEXUS_AI_MAAS_TEMPERATURE` | 验收模板与 `application-prod.yml` 都有（默认 0.1），**方案第 7 节清单漏列** | 本文件登记为第 26 项；不跨线改方案主文档，建议主控在终检时补入第 7 节 |
| `NEXUS_AI_DEMO_SOURCE_ID` | 验收模板独有，是 `verify-real-call.sh` / `schema_check.py` 的验收入参，**不是服务配置项** | 登记为验收专用项，明确不属方案第 7 节「配置变量」，避免误填进生产 env |

差异均不影响券到后的开通与验收：模板能独立跑通真调用，prod 能独立启动，方案清单作为全集仅缺一个可选温度参数。

## 4. 券到后填值顺序（避免漏项）

1. 本地验收：复制 `real-call.env.template` → `real-call.env`，填 14~23 项 + `NEXUS_AI_DEMO_SOURCE_ID`（第 26 项温度可选），运行 `verify-real-call.sh`。
2. 生产部署：ECS `/etc/nexus/nexus.env`（权限 600）填 1~24 项；`NEXT_PUBLIC_API_BASE_URL` 在 `next build` 时注入。
3. 终检：`git grep` 确认无任何真实值入库；`deploy/nexus-deploy.sh health` 全绿。

# NEXUS 华为云接入与部署方案

> 编写人：成员 A（后端与云架构）　日期：2026-11-23　状态：**方案，待拍板后实施**（云部署暂停：代金券未发放，券到后按本方案执行）
> 输入：用户 2026-11-23 拍板——部署形态 ① CodeArts 流水线；云资源现状仅华为云账号；区域由 A 决定。

## 1. 目标与范围

把 NEXUS 从「本地 H2 + mock」推进到「华为云真实服务 + 公网可访问 Demo」，并产出比赛要求的云资源佐证材料。

- **范围内**：云资源开通清单、代码适配、CodeArts 流水线、部署脚本、端到端验收。
- **范围外**：风险规则变更、Agent 授权级别调整（仍 L0–L2、不碰支付）、数据库表结构变更。
- **基线**：先合并 PR #12（`feature/backend-a-events`）与 B 的 `feature/ai-b`，云部署基于合并后的 `develop`。

## 2. 现状盘点：代码就绪度

| 能力 | 实现 | 配置入口 | 就绪度 |
| --- | --- | --- | --- |
| 华为云 OCR | `HuaweiOcrProvider`（IAM 换 Token） | `NEXUS_AI_OCR_*` | ✅ 代码就绪，缺实例与凭据 |
| MaaS 抽取 | `MaasLlmProvider`（OpenAI 兼容） | `NEXUS_AI_MAAS_*` | ⚠️ 端点/path 需适配（见 3.2） |
| OBS 文件存储 | `ObsStorageProvider`（esdk-obs-java 3.26.9） | `NEXUS_OBS_*` | ✅ 代码就绪，缺桶与 AK/SK |
| RDS PostgreSQL | `application-prod.yml` | `NEXUS_RDS_*` | ✅ 代码就绪，缺实例；首次需建表 |
| Redis | `application-prod.yml` | `NEXUS_REDIS_*` | ✅ 代码就绪，缺实例 |
| 云连通性日志 | `CloudConnectChecker`（RDS/Redis） | 启动自动跑 | ⚠️ OBS 探活 TODO |
| CORS | `SecurityConfig.java:56` 写死 localhost | 无 | ❌ 上云必改 |
| 前端 API 地址 | `http.ts:9`、`upload.ts:54` 已支持环境变量 | `NEXT_PUBLIC_API_BASE_URL` | ✅ 构建期注入即可，无需改码 |
| 部署载体 | 无 Dockerfile / 部署脚本 / 流水线 | 无 | ❌ 本次新建 |

## 3. 关键决策

### 3.1 区域：cn-north-4（华北-北京四）

| 理由 | 说明 |
| --- | --- |
| OCR / IAM 默认值 | `AiProperties.Ocr` 默认端点已是 `ocr.cn-north-4`、`iam.cn-north-4` |
| OBS 默认值 | `application-prod.yml` 默认 `obs.cn-north-4.myhuaweicloud.com` |
| CodeArts / RDS / Redis | 该区域均可用，资源可同 VPC 内网互通 |
| 规避风险 | 统一区域避免跨区内网不通、`projectId` 错配、额外时延 |

MaaS 中国站新版为全局端点（`api.modelarts-maas.com`），不受此区域选择影响；若用旧版区域端点，也一并使用 `cn-north-4`。

### 3.2 MaaS 端点适配（重要）

代码现状：`AiProperties.Maas.baseUrl = https://maas-api.cn-southwest-2.myhuaweicloud.com`，`MaasLlmProvider.COMPLETION_PATH = "/v1/chat/completions"`。

华为云中国站现行 MaaS（ModelArts Studio）文档的调用地址为：

```
POST https://api.modelarts-maas.com/v2/chat/completions
Authorization: Bearer $MAAS_API_KEY
```

两代接口 **path 不同（`/v1` vs `/v2`）**，直接把新域名填进 `baseUrl` 会拼出 `/v1/` 而 404。本方案处理：

1. `AiProperties.Maas` 新增 `completionPath`，默认 `/v1/chat/completions`（保持旧行为）。
2. `MaasLlmProvider` 的 path 常量改为读配置。
3. 以控制台模型「API调用」面板给出的**实际 URL** 为准：新端点填 `baseUrl=https://api.modelarts-maas.com`、`completionPath=/v2/chat/completions`；旧端点填区域域名 + `/v1/chat/completions`。

### 3.3 代码源：GitHub 直连，不迁移仓库

CodeArts Build 支持 GitHub 代码源。项目三人协作已固定在 GitHub PR 流程，若改为 CodeArts Repo 会破坏现有协作与提交记录。方案：CodeArts 项目绑定 GitHub 仓库 `develop` 分支；仅在 GitHub 拉取不稳定时，再镜像到 CodeArts Repo（列为备选，不默认执行）。

### 3.4 部署拓扑（单机起步）

```
公网 EIP
  ├─ :80/:443  nginx  →  Next.js (next start, :3000)
  └─ /api/*    nginx 反代 →  Spring Boot jar (:8080)
                              ├─ RDS PostgreSQL（内网）
                              ├─ Redis（内网）
                              ├─ OBS（公网端点）
                              └─ MaaS / OCR（公网端点）
```

前端 `NEXT_PUBLIC_API_BASE_URL` 在构建期注入 EIP 地址；nginx 同源反代为可选优化，不改变现有 `http.ts`。

## 4. 云资源开通清单（当前仅有账号）

| # | 资源 | 建议规格 | 用途 | 凭据落点 |
| --- | --- | --- | --- | --- |
| 1 | CodeArts | 免费版 | 构建 + 部署流水线 | 控制台授权 |
| 2 | ECS | 2vCPU/4GiB，系统盘 ≥80GiB，Ubuntu/CentOS，绑 EIP | 前后端运行载体 | SSH 密钥 |
| 3 | VPC / 安全组 | 默认 VPC + 安全组放行 22/80/443/8080 | 网络与访问控制 | — |
| 4 | RDS PostgreSQL | 基础版，与主账号同区域 | 生产库 | `NEXUS_RDS_*` |
| 5 | Redis | 基础版 1GB 起，同区域 | 退出黑名单 | `NEXUS_REDIS_*` |
| 6 | OBS 桶 | 标准存储，同区域，桶名全局唯一 | 上传文件 | `NEXUS_OBS_BUCKET/AK/SK` |
| 7 | MaaS | 模型广场开通任一文本模型（如 GLM/Kimi）+ API Key | 结构化抽取 | `NEXUS_AI_MAAS_*` |
| 8 | OCR | 开通通用文字识别 + 专用 IAM 用户（最小权限） | 图片/PDF 识别 | `NEXUS_AI_OCR_*` |
| 9 | IAM AK/SK | 专用于 OBS 的 IAM 用户 | 对象存储签名 | `NEXUS_OBS_AK/SK` |

**成本提醒**：以上多为按量计费，MaaS 按 Token、OCR 按次计费。Demo 验收后及时释放按需资源；比赛答辩前保留最低可用集。国内 EIP 用 80 端口需域名备案，演示阶段可先用 `EIP:8080` 直连，避免备案周期。

## 5. 代码改造清单（拍板后执行）

| # | 文件 | 改动 | 优先级 |
| --- | --- | --- | --- |
| 1 | `SecurityConfig.java` | CORS 白名单改可配置 `NEXUS_CORS_ORIGINS`，默认保留 localhost | P0 |
| 2 | `AiProperties.java` | `Maas` 增 `completionPath` 字段 | P0 |
| 3 | `MaasLlmProvider.java` | path 常量改读配置，兼容 v1/v2 | P0 |
| 4 | `application-prod.yml` | 显式 `nexus.ai.mode: real`；补 CORS/凭据占位 | P0 |
| 5 | `deploy/`（新增） | systemd unit + nginx 配置 + 启停脚本 | P0 |
| 6 | `CloudConnectChecker.java` | 补 OBS 探活（阶段 5 收尾） | P1 |
| 7 | `nexus-frontend/` | **无需改码**，构建时注入 `NEXT_PUBLIC_API_BASE_URL` | — |

首次部署 RDS 时必须手工执行 `init-postgres.sql`（prod 为 `sql.init.mode=never`），否则空库无表、启动即失败。

## 6. CodeArts 流水线配置步骤

1. **开通与建项**：开通 CodeArts 免费版 → 新建 Scrum 项目（如 `NEXUS`）。
2. **绑定代码源**：持续交付 → 编译构建 → 新建任务，代码源选 GitHub，仓库选 `HarmonyICT`，默认分支 `develop`，模板选 Maven。
3. **构建任务**：构建命令 `mvn -q clean package -pl nexus-backend -am`；归档 `**/target/*.jar`。前端构建任务：`npm ci && npm run build`，注入 `NEXT_PUBLIC_API_BASE_URL`。
4. **部署任务**：基础资源管理 → 新建主机集群并加入 ECS → 新建部署应用，来源选构建任务，步骤为「上传包 → 备份旧包 → systemd 重启 → 健康检查」。
5. **流水线串联**：新建流水线，阶段为「构建 → 部署」，用流水线参数传递版本号，保证构建与部署包一致。
6. **凭据注入**：所有密钥在 CodeArts「环境变量/凭据管理」与 ECS 的 `/etc/nexus/nexus.env`（权限 600）中配置；**禁止写入仓库或流水线文本**。

## 7. 环境变量清单

| 变量 | 来源 | 备注 |
| --- | --- | --- |
| `NEXUS_JWT_SECRET` | 自生成 ≥32 字节随机串 | 生产必填 |
| `NEXUS_RDS_HOST/PORT/DB/USER/PASSWORD` | RDS 实例 | 内网地址优先 |
| `NEXUS_REDIS_HOST/PORT/PASSWORD` | Redis 实例 | — |
| `NEXUS_OBS_ENDPOINT/BUCKET/AK/SK` | OBS + IAM | AK/SK 最小权限 |
| `NEXUS_AI_MAAS_BASE_URL/API_KEY/MODEL` | MaaS 控制台 | 模型名从「API调用」面板取 |
| `NEXUS_AI_MAAS_COMPLETION_PATH`（新增） | 按控制台实际 URL | `/v1` 或 `/v2` |
| `NEXUS_AI_OCR_IAM_ENDPOINT/ENDPOINT/USERNAME/PASSWORD/DOMAIN_NAME/PROJECT_ID` | OCR + IAM | 专用最小权限用户 |
| `NEXUS_CORS_ORIGINS`（新增） | EIP/域名 | 逗号分隔 |
| `NEXT_PUBLIC_API_BASE_URL` | 前端构建期 | 后端公网地址 |

本地验收可复用 `nexus-ai/real-call.env.template` + `verify-real-call.sh`（复制为 `real-call.env` 填值，文件已被 `.gitignore` 忽略）。

## 8. 执行阶段

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| 0 | 合并 PR #12 与 B 分支到 develop | 部署基线（已完成：基线 = `f3c624f`，PR #12/#13 已合入；PR #14 E2E 测试评审中） |
| 1 | 开通 4 节资源，凭据填本地 env（不入库） | 资源清单与连接信息 |
| 2 | 执行 5 节代码改造，本地构建验证 | 窄范围 PR |
| 3 | 本地连真实云服务跑 `verify-real-call.sh` + `postgres` profile | 真实调用验收记录 |
| 4 | 配置 CodeArts 流水线，部署 ECS，建表，端到端验收 | 公网 Demo + 截图 |
| 5 | 补 OBS 探活、整理日志与截图，回填收集表部署链接 | 比赛佐证材料 |

## 9. 验收清单

- [ ] `/actuator/health` = UP；启动日志「云资源连通性 [rds]/[redis]/[obs]」均 OK
- [ ] 注册/登录/JWT 正常，`user_id` 数据隔离有效
- [ ] 上传真实资料 → OCR 识别 → MaaS 抽取 → 实体/事实/事件落库
- [ ] 事件时间线可显示；规则产险带证据与 `rule_code`
- [ ] Agent 授权操作限于 L0–L2；无支付类动作
- [ ] 前后端跨域正常，公网可访问
- [ ] 仓库 `git grep` 无明文密钥

## 10. 红线

1. 风险结论只由规则产生，模型仅做解释——切真实 MaaS 后不变。
2. Agent 仅 L0–L2，不碰支付交易。
3. 凭据只进环境变量与控制台凭据管理，不进仓库、不进日志、不进对话。

## 11. 待拍板

| # | 事项 | A 的建议 |
| --- | --- | --- |
| 1 | 本方案是否照此执行 | ①批准（推荐）②调整后再定 |
| 2 | 代码源 | ①GitHub 直连（推荐）②镜像到 CodeArts Repo |
| 3 | 演示端口 | ①EIP:8080 先跑（推荐）②已有备案域名，走 80/443 |
| 4 | 阶段 3 真实调用验收由谁执行 | ①A 本地执行并留档（推荐）②B 执行 |

## 12. 变更记录

| 版本 | 日期 | 说明 | 作者 |
| --- | --- | --- | --- |
| v0.1 | 2026-11-23 | 首版：现状、区域/端点决策、资源清单、改造点、流水线、验收 | A |
| v0.2 | 2026-11-23 | 阶段 0 完成（基线 `f3c624f`）；标注代金券未发放、云部署暂停 | A |

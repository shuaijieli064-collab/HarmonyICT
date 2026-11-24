# NEXUS · 个人资料智能管家

> 华为 ICT 创新赛 · 创新赛赛题1「基于华为云产品打造 AI 创新应用」参赛作品。
> 一句话定位：**上传资料 → AI 抽取结构化事实 → 个人世界模型 → 主动风险发现 → 授权 Agent 生成行动。**

---

## 1. 从想法到应用

### 1.1 想法：资料很多，但"知道自己有什么、该做什么"很难

个人的发票、订阅、保单、学习计划散落在不同文件里，没人把它们连成一张可推理的图。
NEXUS 的想法是：把资料交给系统，系统抽取成**可溯源的事实**，再基于规则主动发现风险，最后在用户授权下把风险转成待办。

### 1.2 收敛：只做一条能演示完的闭环

六引擎 + 完整 Agent 的愿景超出三人 + 赛程承载力，比赛版砍掉支付/外部高风险操作、砍掉多方案模拟，
只保留下面这一条**唯一核心闭环**（所有开发服务于它）：

```
用户上传凭证/资料(PDF/图片/TXT)
        ↓  OBS 存原文件
华为云 OCR + MaaS 大模型抽取结构化信息
        ↓
写入 RDS(PostgreSQL)：实体 / 事实 / 事件 / 来源
Redis 缓存风险状态与任务状态
        ↓
前端可视化：世界模型图谱 + 时间线 + 风险中心
        ↓
系统展示风险（含触发规则与证据来源）
        ↓
用户授权 → Agent 生成待办 / 更新记录 → 留存执行日志
        ↓
前端展示执行结果，世界模型更新
```

### 1.3 应用：驾驶舱 + 五页产品形态

| 页面 | 路由 | 作用 |
|---|---|---|
| 个人驾驶舱 | `/` | 一屏看清状态 → 风险 → 行动 |
| 世界模型 | `/world-model` | 实体关系图谱 + 事实列表 + 来源与置信度 |
| 情报中心 | `/intelligence-center` | 风险等级、触发规则、关联事实、确认/解决 |
| 决策实验室 | `/decision-lab` | 洞察引擎族概览与未来时间线 |
| Agent 中心 | `/agent-center` | 执行记录、操作日志、L2 授权二次确认 |
| 资产中心 | `/asset-center` | 资产事实表格 |
| 登录 / 注册 | `/login` | JWT 认证入口 |

> 视觉规范见 [`docs/design-system.md`](docs/design-system.md)（暗夜玻璃，深浅双主题）；API 契约见 [`docs/api-specification.md`](docs/api-specification.md)。

### 1.4 分工与工程化

三人并行：A 后端与云架构、B AI 与智能引擎、C 前端与产品。
统一仓库用 `main / develop / feature` 分支 + PR 合入；CodeArts 代码智能体贯穿需求、编码、修复、构建、部署全流程。

---

## 2. 技术架构

| 层 | 选型 | 说明 |
|---|---|---|
| 前端 | Next.js 16 + React 19 + TypeScript + Tailwind CSS 4 + Framer Motion + ECharts 6 | 桌面/移动浏览器兼容，`nexus-frontend/` |
| 后端 | Java 21 + Spring Boot 3.3.5 + Spring Security + JWT | 模块化单体，不拆微服务，`nexus-backend/` |
| 持久层 | MyBatis-Plus + H2（local）/ PostgreSQL（prod） | 业务表按 `user_id` 隔离 |
| 缓存 | Redis | 风险状态、任务状态、会话 |
| 文件 | 华为云 OBS | 原始文件与图片 |
| AI | MaaS 大模型 + OCR，统一封装 AI Provider | 本地 mock 模式可无凭据跑通全链路 |
| 研发 | CodeArts 代码智能体 | 需求 / 编码 / 修复 / 构建 / 部署 |

---

## 3. 华为云产品使用清单

| 华为云产品 | 在本项目中的真实用途 | 证据 |
|---|---|---|
| **CodeArts 代码智能体**（必选） | 需求拆解、代码生成、问题修复、构建与部署辅助 | `docs/evidence/cloud/codearts/` |
| **RDS for PostgreSQL** | 世界模型 9 张业务表、事实/风险/任务的持久化 | `docs/evidence/cloud/rds/` |
| **Redis 云服务** | 风险状态、任务状态缓存 | `docs/evidence/cloud/redis/` |
| **OBS 对象存储** | 上传的原始文件/图片落桶，与事实的 sourceId 关联 | `docs/evidence/cloud/obs/` |
| **MaaS 模型推理 + OCR** | 资料结构化抽取；模型只做理解与解释，不做风险结论 | `docs/evidence/cloud/maas/` |
| **ECS + nginx**（部署） | 前后端公网部署与健康检查 | `docs/evidence/cloud/deploy/` |

> 证据目录命名规范见 [`docs/evidence/README.md`](docs/evidence/README.md)。

---

## 4. 本地运行

**前置**：Node.js 20+、Java 21+、Maven 3.9+。

```bash
# 一键启动（后端 local profile：H2 内存库 + mock 抽取；前端 next dev）
bash scripts/dev-start.sh
# Windows: scripts\dev-start.bat

# 停止
bash scripts/dev-stop.sh
```

- 前端：<http://localhost:3000>
- 后端健康检查：<http://localhost:8080/actuator/health>
- 后端默认 `local` profile 使用 H2 + 确定性 mock 抽取，**无需华为云凭据即可跑通上传→抽取→落库全链路**；
  真实联调需显式覆盖 `--nexus.ai.mode=real`，凭据只走环境变量，不进仓库。
- 一键脚本中的 Maven 路径可通过 `MVN_CMD` 环境变量覆盖，例如 `MVN_CMD=mvn bash scripts/dev-start.sh`。
- 前端单独构建：`cd nexus-frontend && npm ci && npm run build && npm run lint`。

---

## 5. 不变红线

1. **风险结论只由规则引擎产生**，模型仅做理解与解释，不输出随机风险判断。
2. **Agent 仅 L0–L2**：读取/建议/内部操作需用户确认，不碰支付与外部高风险操作。
3. **凭据只进环境变量与凭据管理**，不进仓库、日志、对话；`git grep` 终检无明文密钥。
4. 所有业务数据按 `user_id` 隔离，重要事实可定位到来源，风险展示触发规则与证据。

---

## 6. 文档索引

- [`docs/NEXUS-立项与三人推进计划.md`](docs/NEXUS-立项与三人推进计划.md) — 赛题对齐、范围收敛、核心闭环
- [`docs/NEXUS-V2-V3-整合规划方案.html`](docs/NEXUS-V2-V3-整合规划方案.html) — 做什么
- [`docs/NEXUS-并行执行编排.html`](docs/NEXUS-并行执行编排.html) — 怎么并行做
- [`docs/api-specification.md`](docs/api-specification.md) — API 契约
- [`docs/database-design.md`](docs/database-design.md) — 表结构
- [`docs/design-system.md`](docs/design-system.md) — 视觉规范
- [`docs/demo-script.md`](docs/demo-script.md) — 演示脚本
- [`docs/evidence/README.md`](docs/evidence/README.md) — 证据命名规范

## 许可证

[Apache License 2.0](LICENSE)

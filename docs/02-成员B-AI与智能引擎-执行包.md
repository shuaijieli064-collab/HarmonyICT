# 成员 B · AI 与智能引擎执行包

> 定位：**让系统真正具备智能能力**。负责华为云大模型/OCR 接入、信息抽取、风险规则、Agent 编排。
> 用法：把下面「CodeArts 启动 Prompt」整段复制给 CodeArts 代码智能体，作为第一个任务下发。

---

## CodeArts 启动 Prompt（直接复制执行）

```
你是 NEXUS 项目的 AI 与智能引擎工程师。项目是华为 ICT 创新赛赛题1「基于华为云产品打造 AI 创新应用」的参赛作品，形态为 Web 可视化 Demo，核心闭环是：上传资料→AI 抽取结构化事实→入库→风险发现→授权 Agent 生成待办。技术栈：后端 Java 21 + Spring Boot，AI 能力走华为云 MaaS 大模型推理 + OCR。

【第一步：读取上下文】
先读取 docs/04-代码规范.md、docs/00-GitHub协作与整合约定.md、docs/NEXUS-立项与三人推进计划.md，理解代码规范、整体架构、目录边界和整合点。你生成的所有代码必须严格遵守 docs/04-代码规范.md（尤其第 6 节 AI 模块规范）。严格遵守：你只写 nexus-backend 的 com.nexus.ai 包（A 已建接口骨架）和 docs/extraction-schema.md、docs/ai-evaluation.md；不要修改后端其他包（成员 A 负责）和 nexus-frontend/（成员 C 负责）。如果 com.nexus.ai 接口骨架尚未存在，先与成员 A 确认接口签名，不要自行改动 A 的文件。

【本轮任务：阶段一 AI 接入 + 抽取 Schema】
1. 验证华为云 MaaS 大模型与 OCR 可调用；实现 AI Provider 接口（对接 com.nexus.ai 中 A 定义的签名），统一封装为可替换接口，业务代码不直接依赖具体模型厂商。
2. 设计结构化信息抽取 Schema，输出 docs/extraction-schema.md：定义从 PDF/图片/TXT 中抽取的字段（商品名称、购买日期、价格、商家、保修期限、订阅、目标、任务、来源文件等），明确字段类型、必填性、置信度取值范围。这份 Schema 要能映射到 A 的 world_facts 表字段，请对照 docs/database-design.md（若尚未产出，先按立项文档的字段定义设计）。
3. 实现一个文档信息抽取 Demo：输入一份测试图片/PDF，输出符合 Schema 的 JSON，并含每条事实的 source_id 与 confidence。
4. 准备测试资料集（PDF/图片/TXT 各 1 份）放在约定目录，供联调使用。

【后续阶段任务（本轮先规划，不急于实现）】
- 风险规则引擎：期限监控、日程冲突、保修/订阅到期、预算约束 4 类风险。关键：风险判断必须由规则/算法给出，大模型只负责理解与解释，禁止让模型随机生成结论。每条风险要能输出触发原因、关联事实、证据来源。
- Agent 工具编排：仅 L0-L2（读取/建议/内部操作需用户确认），实现创建待办、更新世界模型、记录执行日志，保留授权与操作痕迹。

【Git 约定】
在 feature/ai-b 分支开发，commit 格式 [B] 类型: 描述，每天收工前 push。extraction-schema.md 与 ai-provider 接口变更发 PR 到 develop 供 A review。需要拉取/推送时，严格按 docs/05-智能体协助Git拉取与推送说明.md 执行，并遵守其中第 2 节安全红线。

【完成标准】
MaaS/OCR 调用验证通过；抽取 Schema 产出且字段可映射到 world_facts；Demo 能对测试资料输出带来源和置信度的结构化 JSON；不触碰 A、C 的文件。完成后汇报已创建文件清单与待确认的接口问题。
```

---

## 任务清单（分阶段）

| 阶段 | 任务 | 优先级 | 产出 |
| --- | --- | --- | --- |
| 一 | MaaS 大模型 + OCR 调用验证 | P0 | 调用验证记录 |
| 一 | AI Provider 接口实现（可替换封装） | P0 | AI 服务层 |
| 一 | 抽取 Schema 设计（字段映射到 world_facts） | P0 | `extraction-schema.md` |
| 一 | 文档抽取 Demo + 测试资料集 | P0 | 抽取 JSON + 测试集 |
| 三 | 4 类风险规则引擎（规则驱动，非模型生成） | P0 | 风险分析引擎 |
| 四 | Agent 工具注册 + 编排（L0-L2） | P0 | Agent 执行模块 |
| 五 | AI 输出质量测试 + 提示词优化 | P1 | `ai-evaluation.md` |

## 交付给 who

- 给 **A**：抽取 Schema 到 world_facts 的字段映射、AI Provider 实现（供后端注入）。
- 给 **C**：抽取结果 JSON 样例（前端展示结构化事实与来源用）。

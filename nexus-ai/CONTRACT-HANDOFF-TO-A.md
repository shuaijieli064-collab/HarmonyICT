# 契约差异移交清单：B → A

> 发起人：成员 B（AI 与智能引擎）　日期：2026-11-22　分支：`feature/ai-b`
> 依据：`docs/extraction-schema.md` 第 12 节（11 项对齐结果）、`nexus-ai/README.md`
> 用途：请 A 在 `feature/backend-a` 上评估并确认以下契约差异。B 不修改 A 独占的 DDL / DTO / 数据库设计文档，全部以本清单提出，整合时经 PR 三方确认。

> **状态更新（2026-11-22）**：P0 全部 4 项（含新增的第 0 项）已由 A 在 `a6e5c92` + `6301d9b` 关闭并核实，详见各条 ✅ 标记。P1 四项已填入 B 的倾向，供 A 在 PR 统一拍板。B 侧已按拍板完成适配（sourceId 雪花串透传、90011-90013 抛码、文档示例与 obs_key 去 UUID），见 `06334b3`。

---

## 背景

B 已完成抽取输出的全链路 `sourceId` 补齐（验收项 3 可追溯），mock Demo 实测 entity + 3 fact + 1 event 均带 `sourceId`。但当前 A 的 DTO 与 DDL 相比 B 的抽取 Schema 仍缺若干字段，B 侧现通过 `extra` 透传保证数据不丢，**尚未落库**。以下按紧急度列出需 A 决策/落地的差异。

---

## P0 — 阻塞「抽取结果入库」（✅ 已全部关闭，2026-11-22）

### 0. ✅ 已关闭：`source_id` 类型三方不一致（2026-11-22 review 发现）

这是比下列差异更优先的阻塞项，A 在 `f8acddc` 刚补充了 BIGINT 字符串约定，冲突因此坐实：

| 来源 | `source_id` 类型 |
| --- | --- |
| B 的 `extraction-schema.md`（第 44/96/115/133/148 行） | `string(uuid)`，如 `00000000-0000-4000-8000-000000000001` |
| A 的 `database-design.md` 第 4 节 | `world_sources.id BIGINT PRIMARY KEY`、`world_facts.source_id BIGINT REFERENCES` |
| A 的 `api-specification.md` + `JacksonConfig.java`（`f8acddc`） | 所有 `*Id` 字段序列化为**字符串**（雪花 ID 防 JS 精度丢失），即 BIGINT 的数字字符串 |

**后果**：B 产出的 UUID 字符串无法作为外键写入 A 的 `world_facts.source_id BIGINT` 列，联调时事实入库直接失败。

**需 A 拍板（三选一）**：
1. A 把 `world_sources.id` / 外键改为 UUID 或 `VARCHAR(36)`（贴合 B 的 schema，改动面涉及 A 的 DDL 与 API 示例）；
2. B 把 `source_id` 改为数字字符串（B 的 schema 降级，与 UUID 追溯语义冲突，不推荐）；
3. 增加「UUID ↔ 雪花 ID」映射表，抽取层保留 UUID、入库层转换。

> **✅ 拍板结论（A，2026-11-22）**：三选一方案外部份采纳——保持 `BIGINT` 不改 DDL，B 侧透传 A 上传接口返回的雪花数字字符串，禁止自造 UUID。B 已同步 schema 定义、Demo、测试、文档示例与 obs_key，无需映射表。`a6e5c92` 落实。

### 1. ✅ 已关闭：`ExtractionRequest` DTO 增加 `source_id`（对应 schema 第 12 节 #11）

- **现状**：`nexus-backend/src/main/java/com/nexus/ai/dto/ExtractionRequest.java` 只有 `sourceText` / `schemaHints`，无 `source_id`。
- **B 的临时方案**：`extra.sourceId` 透传；Demo 用 `nexus.ai.demo.source-id`（默认演示 UUID）注入。
- **✅ 已落实**：`ExtractionRequest` 已加 `String sourceId`（`a6e5c92`），B 侧 Provider 用 `request.sourceId()` 透传并对空值抛 90011，测试覆盖。
- **影响**：不修则 Provider 入库时拿不到来源 ID，`world_facts.source_id` 无法填写，可追溯性断裂。

### 2. ✅ 已关闭：`world_facts` 补列（对应 schema 第 12 节 #3、#6）

- **现状**（`db/init-postgres.sql`）：`world_facts` 无 `value_type`、`unit`、`normalized_value`、`fact_status`、`evidence` 列。
- **B 输出中这些字段均存在**（见 `extraction-schema.md` 4.3 节与 `testdata/extraction-result.json`）。
- **✅ 已落实**：`value_type VARCHAR(16)` / `unit VARCHAR(16)` / `normalized_value TEXT` / `fact_status VARCHAR(16)` / `evidence TEXT` 五列已补入 `world_facts`（`a6e5c92`，`schema.sql` 与 `init-postgres.sql` 双份），B 侧 `extra` 字段现可直接落库。`fact_status` 三态 `confirmed/inferred/unverified` 已与 B 的 `extraction-schema.md` 核对一致。**待 A**：为该三态补 CHECK 兜底，注意约束名不能复用已被 `status` 列占用的 `chk_fact_status`。
- **影响**：不修则这些字段只能随 `extra` 透传，无法落库、无法检索。

### 3. ✅ 已关闭：`world_sources` 去重字段（对应 schema 第 12 节 #5）

- **现状**：无 `sha256`、`page_count`、`uploaded_at`。
- **✅ 已落实**：三列已补入 `world_sources`（`a6e5c92`，含 `(user_id, sha256)` 索引），并建 `idx_ws_user_sha256`。B 侧输出已带这三个字段，可直接落库。

---

## P1 — 联调阶段确认（不阻塞当前交付）

| # | 议题 | B 的现状 | 需 A 确认 | B 的倾向（供 PR 拍板） |
| --- | --- | --- | --- | --- |
| 4 | entity `aliases` | 暂并入 `attributes` JSONB | 是否需要独立 `aliases` 列 | **倾向不加列**：别名是展示/检索增强，JSONB 已够用；真需要精确检索时建 GIN 索引比拆列划算 |
| 5 | 事实唯一键 | 应用层按 `(user_id, source_id, subject_id, predicate, valid_from)` 判重 | DDL 是否补唯一约束（现无） | **倾向补唯一约束**：当前纯应用层判重在并发抽取下有重复风险；但 `valid_from` 可空，PG 唯一约束对 NULL 不去重，需配合 `COALESCE` 表达式索引，建议联调阶段定 |
| 6 | `world_risks` 关联事实 | 风险输出带证据事实 ID（存 `evidence`） | 是否补 `fact_ids` 关联列（现无） | **倾向补关联表而非列**：一条风险可对应多事实，数组列违反范式且难反查；建议 `world_risk_facts(risk_id, fact_id)` 关联表，联调后再定 |
| 7 | 实体跨文档合并 | 应用层按 `(user_id, entity_type, name)` 判重 | 与 A 文档约定是否一致 | **倾向保持一致**：现有约定可支撑阶段四；但 `name` 需先做规范化（trim + 全半角/大小写归一）再比对，否则"华为 / 华为 "会重复，建议联调补一条归一化规则 |

> `object_value` 用 TEXT、predicate 无字典表、source_id 由 A 上传时生成、日期推断降置信度等项双方已对齐，无需变更。
>
> 以上 4 项均不阻塞阶段四联调，B 已给出倾向供 A 在 PR 里统一拍板；拍板后如需 B 侧改抽取输出映射，B 再跟进。

---

## B 侧已落实（无需 A 处理）

- `docs/extraction-schema.md` 第 12 节 #11 已登记 `sourceId` 约定与差异；13.2/13.3 示例与 obs_key 的 UUID 残留已全量改为雪花串。
- `extraction.md` 提示词、`RuleBasedExtractor`、`MockLlmProvider`、`ExtractionDemoRunner`、`ExtractionOutputParser` 全链路透传 `sourceId`（雪花串）。
- 90011 / 90012 / 90013 已按 `ResultCode.java` 抛出；`E-PREDICATE` 由 Parser 逐条丢弃改为 Provider 对外 90013 硬阻断（不重试），单测覆盖。
- `DefaultAgentOrchestratorProvider` 在 `4e8e34c` 已实现，6 个单测覆盖 L0/L1/L2 计划产出；本次新增 `AgentOrchestrationRunnerContractTest`，用 mock mapper + 真实 Provider 验证 A 的 Runner 经 `ObjectProvider` 注入后 `plan()` 在真实 run 上下文的产出与落库链路，**无需 A 改代码**。
- 全量 `mvn test` 通过；本地 `feature/ai-b` ahead origin 8 个提交，未推送。

---

## 建议整合顺序（更新）

1. ~~A 在本分支确认 P0 三项~~ ✅ 已在 `a6e5c92` / `6301d9b` 全部关闭并核实。
2. ~~B 适配 A 的新 DTO 签名~~ ✅ 已在 `06334b3` 完成（`sourceId` 透传、90013、文档去 UUID）。
3. **当前**：A 将 PR #1 合入 develop（`develop` 目前仍停在 `c883022`）。
4. B rebase develop → 提 `feature/ai-b` 的 PR（待 A 完成第 3 步；当前 `gh` 未认证，PR 由用户在网页端创建）。
5. 阶段四联调：4 个 P1 议题由 A 在 PR 统一拍板；L1/L2 pending 动作的真正执行器属 A 侧未落地项，联调前需确认。

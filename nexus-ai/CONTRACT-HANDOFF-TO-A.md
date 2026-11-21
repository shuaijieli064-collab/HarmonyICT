# 契约差异移交清单：B → A

> 发起人：成员 B（AI 与智能引擎）　日期：2026-11-22　分支：`feature/ai-b`
> 依据：`docs/extraction-schema.md` 第 12 节（11 项对齐结果）、`nexus-ai/README.md`
> 用途：请 A 在 `feature/backend-a` 上评估并确认以下契约差异。B 不修改 A 独占的 DDL / DTO / 数据库设计文档，全部以本清单提出，整合时经 PR 三方确认。

---

## 背景

B 已完成抽取输出的全链路 `sourceId` 补齐（验收项 3 可追溯），mock Demo 实测 entity + 3 fact + 1 event 均带 `sourceId`。但当前 A 的 DTO 与 DDL 相比 B 的抽取 Schema 仍缺若干字段，B 侧现通过 `extra` 透传保证数据不丢，**尚未落库**。以下按紧急度列出需 A 决策/落地的差异。

---

## P0 — 阻塞「抽取结果入库」（需 A 修改）

### 0. ⚠️ 新增阻塞：`source_id` 类型三方不一致（2026-11-22 review 发现）

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

> B 不单方修改 schema 或 DDL，此项必须在 PR #1 review 中由 A 定夺后再两边对齐。

### 1. `ExtractionRequest` DTO 增加 `source_id`（对应 schema 第 12 节 #11）

- **现状**：`nexus-backend/src/main/java/com/nexus/ai/dto/ExtractionRequest.java` 只有 `sourceText` / `schemaHints`，无 `source_id`。
- **B 的临时方案**：`extra.sourceId` 透传；Demo 用 `nexus.ai.demo.source-id`（默认演示 UUID）注入。
- **需 A**：给 `ExtractionRequest` 增加 `String sourceId` 字段，并在 A 的上传服务调用抽取时传入 A 生成的真实 UUID（`world_sources.id`）。
- **影响**：不修则 Provider 入库时拿不到来源 ID，`world_facts.source_id` 无法填写，可追溯性断裂。

### 2. `world_facts` 补列（对应 schema 第 12 节 #3、#6）

- **现状**（`db/init-postgres.sql`）：`world_facts` 无 `value_type`、`unit`、`normalized_value`、`fact_status`、`evidence` 列。
- **B 输出中这些字段均存在**（见 `extraction-schema.md` 4.3 节与 `testdata/extraction-result.json`）。
- **需 A（二选一）**：
  - a) 在 `world_facts` 增加 `value_type VARCHAR`、`unit VARCHAR`、`normalized_value TEXT`、`fact_status VARCHAR`、`evidence JSONB`；或
  - b) 建独立证据表 `world_fact_evidence`，把 `evidence` 拆表存；`value_type/unit/normalized_value/fact_status` 仍建议入主表。
- **影响**：不修则这些字段只能随 `extra` 透传，无法落库、无法检索。

### 3. `world_sources` 去重字段（对应 schema 第 12 节 #5）

- **现状**：无 `sha256`、`page_count`、`uploaded_at`。
- **需 A**：视去重需求决定是否补列；B 侧目前按应用层计算，不阻塞抽取主链路。

---

## P1 — 联调阶段确认（不阻塞当前交付）

| # | 议题 | B 的现状 | 需 A 确认 |
| --- | --- | --- | --- |
| 4 | entity `aliases` | 暂并入 `attributes` JSONB | 是否需要独立 `aliases` 列 |
| 5 | 事实唯一键 | 应用层按 `(user_id, source_id, subject_id, predicate, valid_from)` 判重 | DDL 是否补唯一约束（现无） |
| 6 | `world_risks` 关联事实 | 风险输出带证据事实 ID（存 `evidence`） | 是否补 `fact_ids` 关联列（现无） |
| 7 | 实体跨文档合并 | 应用层按 `(user_id, entity_type, name)` 判重 | 与 A 文档约定是否一致 |

> `object_value` 用 TEXT、predicate 无字典表、source_id 由 A 上传时生成、日期推断降置信度等项双方已对齐，无需变更。

---

## B 侧已落实（无需 A 处理）

- `docs/extraction-schema.md` 第 12 节 #11 已登记 `sourceId` 约定与差异。
- `extraction.md` 提示词、`RuleBasedExtractor`、`MockLlmProvider`、`ExtractionDemoRunner`、`ExtractionOutputParser` 全链路透传 `sourceId`。
- 测试 22/22 通过；mock Demo 输出 `extraction-result.json` 可核对。

---

## 建议整合顺序

1. A 在本分支确认 P0 三项 → 修改 DTO / DDL / `database-design.md` / `api-specification.md`。
2. B 在 `feature/ai-b` 适配 A 的新 DTO（签名变化）——**待 A 合入 develop 后 B 再对齐**，避免双向锁死。
3. 三方 PR 合并 develop，完成契约闭环。

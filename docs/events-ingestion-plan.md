# NEXUS · events 接入方案（A 侧后端，草稿待拍板）

> 起草人：成员 A（后端与云架构）　日期：2026-11-23　状态：**已拍板（2026-11-21 用户确认按建议执行 ①①②①①），实施中**
> 依据：`.arts/handoff-to-wordbuddy.md` 第三节三项拍板、`docs/proposal-events-persist.md` 方案 A、`docs/extraction-schema.md`、本地 `develop ed06cf0` 代码复核。
> 前置条件：B 的 `EVENTS-INGESTION-CONTRACT.md`（7 项）与 `ACCEPTANCE-RECORD.md` 尚未 push，远程 `origin/feature/ai-b` 仍为 `b2b7d8e`（11-22）。本方案按口头拍板起草；B push 后须逐项核对，差异以契约文档为准并回写本方案，再开 PR。

---

## 1. 目标与范围

**目标**：把 B 抽取输出的 `events` 贯通到 `world_events`，使 `GET /api/v1/events` 返回真实数据，点亮 C 端驾驶舱「近期事件」时间线；同时统一抽取契约的命名口径。

**本轮做**：

1. `ExtractionResult` 新增 `events` 及四个顶层字段（`extraction_id` / `source` / `language` / `extracted_at`）。
2. Parser 解析并校验 events，完成 `event_type` 10→6 映射、时间归一 UTC、置信度门槛。
3. 编排管线落 `world_events`，建立临时实体 ID → 库主键映射，处理单条失败隔离。
4. 命名口径统一 snake_case（Prompt 模板、本地 mock、Parser、A 侧 `extra` 键同步）。
5. `tasks.source_type` DDL 两处补 `extracted`（**仅预留，本轮不写入 tasks 表**）。

**本轮不做**：

- 抽取 `tasks` 自动落库——按拍板结论先定结构、透传前端展示，须经 C 的 UI 确认后另开 PR。
- `world_events` 加列、`world_facts` 唯一键调整、风险引擎关联事件——均不在本轮。
- 云上部署、real 模式验收——依赖用户指令与 B 的 MaaS 凭据。
- 前端改动——C 端 `listEvents()` 已接真实接口，后端落库即可生效。

---

## 2. 现状（本地代码复核结论）

| 位置 | 现状 | 后果 |
| --- | --- | --- |
| `ai/dto/ExtractionRequest.java` | `ExtractionResult` 仅 `(facts, overallConfidence)` | events 无传输通道 |
| `ai/extraction/ExtractionOutputParser.java` | `parse()` 只遍历 `facts`，字段读 camelCase | 模型/mock 输出的 events 被静默丢弃 |
| `ai/extraction/DefaultExtractionProvider.java` | 注释明确「只映射 facts」 | real 链路同样丢弃 events |
| `ai/mock/RuleBasedExtractor.java` | 已产出 `purchase` / `subscription_renewal` / `deadline` 三类事件（camelCase） | mock 链路有数据但进不了库 |
| `ingestion/pipeline/ExtractionPipelineService.java` | `runPipeline()` 仅 `persistFact()` | `world_events` 恒为空 |
| `resources/ai/prompts/extraction.md` | 模板为 camelCase，`factStatus` 示例写 `extracted \| inferred`（非三态） | 与 schema 第 9 节 JSON Schema 冲突，需随本轮修正 |
| `db/schema.sql` / `db/init-postgres.sql` | `world_events.event_type` CHECK 仅 6 值；`tasks.source_type` CHECK 仅 3 值 | 10 值 event_type 直接入库会触发 CHECK 违规 |
| `nexus-frontend/components/dashboard/dashboard-data.tsx` | 已调用 `listEvents()` 并映射为时间线 | 后端落库后 C 无需改动 |

---

## 3. 契约口径

### 3.1 命名：snake_case 为准

对齐 `extraction-schema.md` 第 9 节 JSON Schema 与库列名。模型 Prompt 示例、本地 mock 输出、Parser 读取、A 侧 `extra` 键统一改为 `schema_version` / `entity_id` / `source_id` / `fact_status` / `valid_from` / `text_snippet` 等。

兼容策略见第 7 节待拍板项 3。若采用双读，Parser 内部统一以 snake_case 为规范键，camelCase 仅作兜底读取，不写回输出。

### 3.2 顶层字段补齐

`ExtractionResult` 增加 `extractionId`（UUID 字符串）、`source`（来源对象）、`language`（BCP 47）、`extractedAt`（ISO 8601 UTC）。本轮**只接收并写入抽取日志，不新增数据库列**；用途是重试追踪与可观测性。

### 3.3 `events` 字段与入库去向

| 抽取字段 | 类型 | 必填 | 入库处理 |
| --- | --- | --- | --- |
| `event_id` | string `event_N` | 是 | 不落库；作批次内关联与幂等判断键（见 3.6） |
| `event_type` | enum（10 值） | 是 | 经 3.4 映射后写 `world_events.event_type`（6 值） |
| `title` | string | 是 | 写 `world_events.title` |
| `start_at` / `end_at` / `due_at` | date-time | 否 | 按 3.5 选一写 `world_events.occurred_at`（UTC） |
| `related_entity_refs` | string[] | 否 | 仅解析 `ent_*`，映射为 `world_events.entity_id`；`fact_*` 引用忽略并记告警 |
| `confidence` | number | 是 | 仅作 `< 0.50` 丢弃门槛；`world_events` 无该列，本轮不落库 |
| `source_id` | snowflake string | 是 | 转 Long 写 `world_events.source_id`；校验属于当前用户 |
| `evidence.text_snippet` | string | 是 | 写 `world_events.description`（≤200 字，满足可追溯）；缺失则该条拒绝 |

> `entity_id` 为单列，关联多个实体时取第一个成功解析的 `ent_*`，其余记 `W-NO-ENTITY` 告警。多实体关联需加关联表，属后续阶段。

### 3.4 `event_type` 10→6 映射表

`world_events.chk_event_type` 仅允许 `education / career / asset / health / contract / other`，映射表写死在代码常量并登记本文档：

| 抽取 `event_type`（10） | 入库 `event_type`（6） | 理由 |
| --- | --- | --- |
| `purchase` | `asset` | 资产取得事件 |
| `payment` | `contract` | 支付节点属合同履约（B 契约 f60bb4d 定稿口径） |
| `subscription_start` | `contract` | 订阅合同生效 |
| `subscription_renewal` | `contract` | 订阅合同续期 |
| `subscription_expiry` | `contract` | 订阅合同终止 |
| `warranty_expiry` | `asset` | 保修依附于资产本体 |
| `deadline` | `other` | 无对应业务分类 |
| `class` | `education` | 教学事件 |
| `exam` | `education` | 考核事件 |
| `other` | `other` | 兜底 |

> 2026-11-23 复核 B 提交 `f60bb4d` 的 `EVENTS-INGESTION-CONTRACT.md` 第 4.1 节：仅 `payment` 一处不同（原推定 `asset`，B 定稿 `contract`），已按 B 口径修正代码与本表；其余 9 项一致。`deadline` 按关联实体细化（B 4.2 建议）本轮未实现，统一落 `other`，留后续 PR。

### 3.5 时间归一规则

1. 时间字符串按 ISO 8601 解析；无时区按 `+08:00` 处理并记 `W-TZ-ASSUMED`。
2. 统一转 `Instant` 存 UTC（与 `world_events.occurred_at TIMESTAMP` 及现有 `WorldEventServiceImpl` 一致）。
3. `occurred_at` 取值优先级：`start_at` > `due_at` > `end_at`。
4. 三个时间字段全缺失：跳过该条并记 `W-MISSING-FIELD`，**不写 `Instant.now()`**——避免时间线出现假数据。`WorldEventServiceImpl.create()` 现有的 null→now 兜底仅服务人工创建接口，抽取链路不走该分支。
5. 解析失败的时间字段按缺失处理，记 `W-AMBIGUOUS-DATE`。

### 3.6 幂等

状态机已挡住常规重抽：`ExtractionPipelineService.processAsync` 对 `status != pending` 的来源直接跳过。因此本轮采用**应用层查重，不加 DDL**：判重键为 `(user_id, source_id, event_type, occurred_at, title)`，与 B 契约 `f60bb4d` 第 6 节一致；2026-11-23 复核后补入 `event_type`（同标题同时间但类型不同的两条事件不再互相误杀）。

`event_id` 作为文档内临时键，用于批次内 `related_entity_refs` 解析与日志追踪，不作为数据库唯一键。严格唯一索引（含 `event_key` 列）仍留赛后，见第 7 节拍板记录。

---

## 4. 改动清单（精确到文件）

| # | 文件 | 改动 | 性质 |
| --- | --- | --- | --- |
| 1 | `nexus-backend/.../ai/dto/ExtractionRequest.java` | `ExtractionResult` 增加 `events` + 4 顶层字段；新增 `ExtractedEvent` record（`eventId/eventType/title/startAt/endAt/dueAt/relatedEntityRefs/confidence/sourceId/evidence`） | 代码 |
| 2 | `nexus-backend/.../ai/extraction/ExtractionOutputParser.java` | 解析 events；10→6 映射；时间归一；证据与置信度门槛；`ent_*` 引用解析结果透传；告警 | 代码 |
| 3 | `nexus-backend/.../ai/extraction/DefaultExtractionProvider.java` | 构造 `ExtractionResult` 时传入 events 与顶层字段 | 代码 |
| 4 | `nexus-backend/.../ingestion/pipeline/ExtractionPipelineService.java` | 新增 `persistEvent()`；复用事实阶段建立的 `ent_* → Long` 映射；单条事件异常隔离 | 代码 |
| 5 | `nexus-backend/src/main/resources/ai/prompts/extraction.md` | 模板字段改 snake_case；补 4 顶层字段；`factStatus` 示例改 `confirmed / inferred / unverified` 三态 | 配置 |
| 6 | `nexus-backend/.../ai/mock/RuleBasedExtractor.java` | mock 输出字段改 snake_case，保持与 Prompt 一致 | 代码 |
| 7 | `nexus-backend/src/main/resources/db/schema.sql` | `chk_task_source` 增加 `extracted`（H2） | DDL |
| 8 | `nexus-backend/src/main/resources/db/init-postgres.sql` | 同上（PostgreSQL） | DDL |
| 9 | `docs/database-design.md` | 第 3 节 `tasks.source_type` 枚举补 `extracted`；注明本轮不写 tasks 表 | 文档 |
| 10 | `docs/api-specification.md` | 第 7 节 `sourceType` 枚举补 `extracted`（仅枚举，不新增接口） | 文档 |
| 11 | `docs/extraction-schema.md` | 第 10.4 节补 events 入库细则（映射表、时间归一、幂等口径） | 文档 |
| 12 | `src/test/.../ExtractionOutputParserTest.java` | events 解析、非法 event_type 映射、无证据丢弃、时区归一 | 测试 |
| 13 | `src/test/.../ExtractionPipelineServiceTest.java` | 事件落库参数捕获、`ent_*` 映射、单条失败隔离 | 测试 |
| 14 | `src/test/.../RuleBasedExtractorTest.java` | mock 输出 snake_case 字段断言 | 测试 |

**改动边界**：单个 PR，分支 `feature/backend-a-events`（从 develop 切），提交信息 `[A] feat: events 入库接入与抽取契约 snake_case 统一`。不触碰 B/C 源码、不动风险引擎、不动前端。

---

## 5. 实施要点

1. **实体映射复用**：`persistFact()` 中 `findOrCreateByNormalizedName()` 已有 `entityType / name → id` 逻辑；在同一管线运行内维护 `Map<String, Long> tempEntityIds`，键为 `ent_N`，供事件直接取用，避免重复查询。
2. **事实先行**：事件解析依赖实体映射，故 `persistFact()` 全部完成后再执行 `persistEvent()`，顺序与 `extraction-schema.md` 第 11 节「entities → facts → events → tasks」一致。
3. **错误隔离**：单条事件的映射/入库异常记 warn 后继续下一条，不改变 `world_sources.status` 的 `parsed` 结果；整份 JSON Schema 错误仍按 90012 走 `failed`。
4. **数据隔离红线**：`user_id` 与 `sourceId` 均来自服务端上下文，`source_id` 归属校验失败时丢弃该条事件，禁止取用抽取结果中的用户标识。
5. **测试基线**：当前 `mvn clean package` 54 测试全绿；本轮新增用例后须保持全绿。构建前确认无运行中的 `java -jar` 占用 `target/*.jar`。

---

## 6. 验收标准

1. `mvn clean package` 全绿（含新增 events 用例）。
2. local + mock 模式启动，上传订阅/发票样例 → `GET /api/v1/events` 至少返回 1 条真实事件，`event_type` 落在 6 值内，`occurredAt` 为 UTC。
3. 同一来源重复触发不产生重复事件（应用层查重生效）。
4. H2 启动无 CHECK 违规；`tasks.source_type` DDL 两处均含 `extracted` 但本轮无任务自动落库。
5. C 端驾驶舱「近期事件」展示真实数据，前端无需改动。
6. real 模式（B 凭据到位后）跑 `verify-real-call.sh`，模型输出 snake_case 能被 Parser 正确解析。

---

## 7. 拍板结论（2026-11-23）

用户确认按 A 的建议执行，逐项结论如下：

| # | 议题 | 拍板选项 | 结论 |
| --- | --- | --- | --- |
| 1 | 事件幂等存储 | ① | 应用层查重、无 DDL；2026-11-23 复核 B 契约后键扩为 `(user_id, source_id, event_type, occurred_at, title)`；严格唯一索引留赛后 |
| 2 | `event_type` 映射 | ② | B 契约 `f60bb4d` 已 push 并复核：仅 `payment` 一处不同，已按其口径改为 `contract` 并回写 3.4 表 |
| 3 | snake_case 迁移 | ① | Parser 双读：snake 优先 + camel 兜底；camel 兜底在端到端验收后移除 |
| 4 | 无时间字段的事件 | ① | 跳过并记 `W-MISSING-FIELD`，不落 `Instant.now()` |
| 5 | `evidence` / `confidence` 落库 | ① | `evidence.text_snippet` 写 `description`，`confidence` 仅作门槛；不加列 |

---

## 8. 顺序与依赖

1. B push `feature/ai-b` → A review 契约 7 项 → 拍板本方案第 7 节 5 项。
2. A 开 `feature/backend-a-events` PR，按第 4 节清单实施，合并 develop。
3. B 做 events 接入后的端到端验收，回填 `ACCEPTANCE-RECORD.md`。
4. C 确认前端时间线效果；如需 `tasks` 自动落库，另开 PR 与 UI 一并评审。

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
| --- | --- | --- | --- |
| v0.1-draft | 2026-11-23 | 首版：范围、现状、契约口径、改动清单、验收、待拍板项 | A |
| v1.0 | 2026-11-23 | 五项拍板确认（①①②①①），进入实施 | A |
| v1.1 | 2026-11-23 | B 契约 `f60bb4d` review 完成：`payment` 映射改 `contract`、幂等键补 `event_type`、范围外事项登记 | A |

---

## 10. B 契约 review 结论（`feature/ai-b@f60bb4d`，2026-11-23）

对 `nexus-ai/EVENTS-INGESTION-CONTRACT.md` 第 7 节 review 清单逐项结论：

| # | 契约事项 | 结论 | 落地情况 |
| --- | --- | --- | --- |
| 1 | `ExtractionResult` 增加 events / tasks | 部分接受 | `events` 已接入；`tasks` 按原拍板本轮不消费（DTO 未加 `ExtractedTask`）。B 第 8 节验收标准未要求任务落库 |
| 2 | `event_type` 10→6 映射 | 已对齐 | 仅 `payment` 一处不同，已由 `asset` 改 `contract`；其余 9 项一致；`deadline` 按关联实体细化留后续 PR，本轮统一 `other` |
| 3 | `entity_id` 为空时是否入库 + 兜底实体 | 暂不采纳兜底 | 事件照常入库、解析失败置 `null` 并记告警；不改风险引擎。B 端到端验收若发现冲突风险因空实体被丢弃，再另开 PR |
| 4 | `occurred_at` 时区口径 | 一致 | `start_at > due_at > end_at`，无时区按 +08:00 归一 UTC |
| 5 | `tasks.source_type` 扩展 `extracted` | 一致 | 两处 DDL 已补；本轮不写 tasks 表 |
| 6 | 事件应用层幂等键 | 已对齐 | 补入 `event_type`，键为 `(user_id, source_id, event_type, occurred_at, title)` |
| 7 | `confidence` / `evidence` 落库 | 一致 | `text_snippet` 写 `description`，`confidence` 仅作门槛，不加列 |

**范围外事项（不在本 PR，留后续拍板）**：

- `ExtractedTask` / `tasks` 列表：B 希望 DTO 一并扩展；本轮按原拍板不消费。若 B 端到端验收要求任务落库（如 `DEADLINE_1D` 的可达性），另开 PR 连 C 端 UI 一并评审。
- 事件兜底实体：B 提示 `entity_id` 为 `null` 时风险侧会丢弃冲突风险；本轮不动风险引擎，以演示资料实际解析结果为准再定。
- 命名风格：B 的 `ACCEPTANCE-RECORD.md` 第 3.3 节建议线上 wire format 为 camelCase，本方案拍板 snake_case，但 Parser 做了双读兼容（snake 优先 + camel 兜底），B 分支的 camelCase 输出可正常消费；real 验收后统一口径并移除兜底。
- B 新增 `budget_limit` 谓词（Schema v0.2）：本 PR 未重复实现；两分支合并预演（`git merge-tree`）无文本冲突，合并结果中 `budget_limit` 与 events 逻辑均保留，合并顺序不影响功能完整性。

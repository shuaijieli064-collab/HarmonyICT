# events / tasks 抽取入库契约（B → A 对齐稿）

> 主笔：成员 B　|　状态：**待 A review**（本文只定义契约，未修改 A 侧代码）|　日期：2026-11-23
> 关联：`docs/extraction-schema.md` 4.4/4.5/10.4 节、`nexus-ai/ORCHESTRATION-CONTRACT.md`
> 触发原因：风险规则 4 类中的**日程冲突**依赖 `world_events`，但当前抽取输出只落 `world_facts`，
> `world_events` 在上传链路没有数据来源，`ScheduleConflictRule` 实际处于"有规则、无输入"状态。

---

## 1. 现状与缺口（已复核代码）

| 环节 | 现状 | 结论 |
| --- | --- | --- |
| B 的 Prompt | 已要求模型输出 `events` / `tasks`（`ai/prompts/extraction.md`） | 模型侧有产出 |
| B 的 Parser | `ExtractionOutputParser` 只解析 `entities` + `facts`，`events` / `tasks` 被忽略 | **B 侧缺口** |
| B 的 DTO | `ExtractionRequest.ExtractionResult` 只有 `facts` | **契约缺口** |
| A 的编排 | `ExtractionPipelineService` 只 `persistFact` | A 侧缺口 |
| 风险管线 | `RiskAnalysisPipelineService.toEventSnapshots` 读 `world_events` | 有消费方，无生产方 |
| 日程冲突规则 | `ScheduleConflictRule` 按 `occurredAt` 同日/同小时判定 | 当前生产链路永远不触发 |

**影响**：演示中"同一天两节课/两个评审"无法在真实链路产生日程冲突风险，只能靠单元测试证明规则本身正确。

---

## 2. 目标链路

```
上传 → SourceTextExtractor → ExtractionProvider.extract
     → ExtractionResult{ facts, events, tasks }
     → A: entities(归一化) → facts → events → tasks（同一事务）
     → RiskAnalysisPipelineService 读 events → SCHEDULE_CONFLICT_*
```

---

## 3. 出参扩展建议（B 侧 DTO，需 A 同步消费）

`com.nexus.ai.dto.ExtractionRequest` 建议扩展为：

```java
public record ExtractionResult(
        List<ExtractedFact> facts,
        List<ExtractedEvent> events,   // 新增，允许空列表，不得为 null
        List<ExtractedTask> tasks,     // 新增，允许空列表，不得为 null
        double overallConfidence) { }

public record ExtractedEvent(
        String subjectType,      // 与 subjectName 一起用于关联实体（entityType 已映射为 A 枚举）
        String subjectName,
        String eventType,        // B Schema 5.3 枚举，A 侧按第 4 节映射
        String title,
        Instant occurredAt,      // B 侧统一归一为 Instant；空值条目不输出
        String description,      // 由 evidence.textSnippet 截断生成，可空
        double confidence,
        Map<String, Object> extra) { }   // sourceId/evidence/relatedEntityRefs 等

public record ExtractedTask(
        String title,
        Instant dueAt,           // 可空
        String priority,         // high / medium / low，可空（A 落库缺省 1）
        String relatedEntityName,
        double confidence,
        Map<String, Object> extra) { }
```

> 兼容性：为减少对 A 现有代码的编译冲击，可先新增两个 record 与带 events/tasks 的工厂方法，
> 保留旧构造器；A 切换完成后删除旧构造器。

B 侧 Parser 需要同步补齐（本次**未实施**，等 A 确认 DTO 形状后一起提交）：
- `events`：校验 `event_type` ∈ 5.3 受控枚举、至少一个时间字段、`evidence.text_snippet` 非空、
  `confidence >= 0.50`（否则丢弃并记 `E-LOW-CONF`）；
- `tasks`：校验 `title` 非空、`priority` 三态、`due_at` RFC3339 可解析；
- 沿用 facts 的降级策略：无证据 → 置信度上限 0.49 后丢弃；关联实体解析失败 → 丢弃该条目并记 `W-NO-ENTITY`。

---

## 4. `event_type` 映射（B Schema → `world_events.event_type`）

A 的 DDL CHECK 只允许 `education / career / asset / health / contract / other`，B Schema 有 10 种，
必须显式映射，禁止直接透传（否则触发 CHECK 违规）。

### 4.1 静态兜底映射

| B `event_type` | A `event_type` | 说明 |
| --- | --- | --- |
| `purchase` | `asset` | 购买资产 |
| `subscription_start` | `contract` | 订阅合约开始 |
| `subscription_renewal` | `contract` | 订阅续费 |
| `subscription_expiry` | `contract` | 订阅到期 |
| `warranty_expiry` | `asset` | 保修属于资产生命周期 |
| `deadline` | `other`（见 4.2） | 按关联实体细化 |
| `payment` | `contract` | 付款节点 |
| `class` | `education` | 上课 |
| `exam` | `education` | 考试 |
| `other` | `other` | 兜底 |

### 4.2 `deadline` 细化规则（建议）

`deadline` 关联实体类型为 `course` / `goal` → `education`；`project` → `career`；
`asset` / `subscription` → `asset` / `contract`；无法判定 → `other`。

---

## 5. 字段落库映射

### 5.1 `world_events`

| 抽取字段 | 表字段 | 处理规则 |
| --- | --- | --- |
| `event_id` | `id` | 文档内临时 ID，入库由 A 生成主键 |
| `event_type` | `event_type` | 按第 4 节映射 |
| `title` | `title` | 直接落库，≤255 字 |
| `description` | `description` | 优先原文证据片段，超 4096 截断 |
| `start_at` / `due_at` / `end_at` | `occurred_at` | **优先级 `start_at` > `due_at` > `end_at`**；三者皆空则不输出该事件 |
| `related_entity_refs` | `entity_id` | 取第一个可解析的关联实体；A 用 `EntityNameNormalizer` find-or-create 后取真实 ID；解析失败置 `null` |
| `source_id` | `source_id` | `Long.valueOf(sourceId)`，B 只透传 |
| `confidence` / `evidence` | 无对应列 | DDL 无列，暂不落库；如需展示，需 A 扩列或写抽取日志 |

> ⚠️ `entity_id` 为 `null` 时，`RiskAnalysisPipelineService.createIfAbsent` 会丢弃该风险
> （幂等键依赖 entityId）。**冲突类事件必须尽量解析出实体**，否则即使入库也无法形成风险。
> 建议 A 为事件建一个兜底实体（如"日程"）或允许 `ScheduleConflictRule` 以 userId 级实体归属。

### 5.2 `tasks`

| 抽取字段 | 表字段 | 处理规则 |
| --- | --- | --- |
| `title` | `title` | ≤255 字 |
| `due_at` | `due_date` | `world.tasks.due_date` 为 DATE，需按**业务时区（+08:00）**取日期，不要直接 `toLocalDate()`（UTC 会少一天） |
| `priority` | `priority` | `high → 2`、`medium → 1`、`low → 0`；缺省 1 |
| — | `status` | 固定 `todo` |
| — | `source_type` | **待 A 拍板**：现有 CHECK 只允许 `manual/risk/agent`，抽取任务语义最接近 `manual`，但建议扩展 `extracted` 枚举（需 DDL 变更） |
| `related_entity_refs` | `related_fact_id` | 若能定位到对应 `task_due_at` 事实，回填 `world_facts.id`；否则 `null` |

---

## 6. 幂等与重复抽取

`world_events` / `tasks` 当前**无唯一约束**，同源重抽会重复插入。建议：

- 事件应用层判重键：`(user_id, source_id, event_type, occurred_at, title)`；
  重抽时先按该键查询，存在则跳过；量大后再补唯一索引。
- 任务应用层判重键：`(user_id, source_id, title, due_date)`。
- 与 facts 一致：同源重抽在一个事务内完成，失败整体回滚，`source.status` 走
  `pending → parsing → parsed/failed`。

---

## 7. 待 A 确认项（review 清单）

| # | 议题 | B 建议 | 是否涉及 DDL |
| --- | --- | --- | --- |
| 1 | `ExtractionResult` 是否接受新增 `events` / `tasks` 列表 | 接受，先加字段后 A 逐步消费 | 否（仅代码） |
| 2 | `event_type` 映射表（第 4 节）是否认可 | 认可；`deadline` 按关联实体细化 | 否 |
| 3 | `world_events.entity_id` 为空时是否入库 | 入库但风险侧会丢弃；建议给事件兜底实体 | 否 |
| 4 | `occurred_at` 时区口径 | 统一按 +08:00 业务日转 UTC 存储，风险规则按 UTC 日分组 | 否 |
| 5 | `tasks.source_type` 是否扩展 `extracted` | 建议扩展；临时可先用 `manual` 兜底 | 是（CHECK 约束） |
| 6 | 是否补 `(user_id, source_id, event_type, occurred_at)` 唯一索引 | 先应用层判重，数据量起来再加索引 | 是（可选） |
| 7 | 事件 `confidence` / `evidence` 是否要落库 | 演示阶段可只写抽取日志；需要展示时再扩列 | 是（可选） |

---

## 8. 验收标准（A 接入后）

1. 上传 `nexus-ai/testdata/demo/schedule-plan.png`，`source.status` 变为 `parsed`；
2. `world_events` 出现 2026-11-25 的 2 条事件（10:00 / 10:30）与 2026-11-26 的 2 条事件；
3. 风险扫描后 `world_risks` 出现 `SCHEDULE_CONFLICT_HOUR`（medium）与 `SCHEDULE_CONFLICT_DAY`（low）；
4. 前端时间线可看到事件标题与来源（`source_id` 可回溯到上传文件）；
5. 重抽同一文件不产生重复事件（幂等键生效）。

---

## 9. 与任务 4 演示资料的对应关系

| 资料 | 事件/任务产出 | 预期风险 |
| --- | --- | --- |
| `demo/schedule-plan.png` | 2 条同日 10:00/10:30 事件；2 条 11-26 事件；1 条任务（11-24） | `SCHEDULE_CONFLICT_HOUR`、`SCHEDULE_CONFLICT_DAY`、`DEADLINE_1D` |
| `demo/budget-subscription.txt` | 2 条订阅续费事件 | `SUBSCRIPTION_EXPIRY_7D/30D`、`BUDGET_OVERSPENT` |
| `demo/warranty-invoice.pdf` | 1 条购买事件、1 条保修到期事件 | `WARRANTY_EXPIRY_30D` |
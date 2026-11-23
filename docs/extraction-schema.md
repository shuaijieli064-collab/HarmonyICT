# NEXUS · 信息抽取 Schema（extraction-schema）

> 主笔：成员 B　|　Review：成员 A　|　版本：v0.2　|　更新日期：2026-11-23
> v0.2 变更：新增受控谓词 `budget_limit`（预算约束风险的数据来源，见 5.2）；其余不变，待 A review。
> 作用：定义「文档 / 图片 / 文本 → 结构化事实」的输出契约，是 AI 抽取层、后端入库层、前端展示层三方共用的数据边界。
> 依赖：`docs/NEXUS-立项与三人推进计划.md`、`docs/04-代码规范.md`。`docs/database-design.md` 尚未产出，本文按立项文档中的 9 张核心表字段设计；A 定稿后如有差异，以本文第 12 节待确认项对齐后升版本。

---

## 1. 目的与范围

**做什么**：把 PDF / 图片 / TXT 三类资料，经 OCR + 大模型抽取为符合本 Schema 的 JSON，再映射为 `world_entities` / `world_facts` / `world_events` / `world_sources` / `tasks` 记录。

**不做什么**：

- 不判定风险。风险由 B 的规则引擎基于入库事实计算，模型不参与结论。
- 不生成执行动作。Agent 仅在用户授权后基于事实生成待办。
- 不引入文档以外的知识。模型只能抽取原文存在或可明确推断的信息，推断项必须降置信度并标记 `fact_status=inferred`。

**核心原则**：

| 原则 | 落地要求 |
| --- | --- |
| 可追溯 | 每条 fact / event / task 必须带 `source_id` 与原文证据片段 |
| 可量化 | 每条 fact 必须带 0–1 的 `confidence` |
| 可入库 | fact 的 `subject_ref` / `predicate` / `object_value` 可直接映射到 `world_facts` |
| 可拒绝 | 不符合 Schema 或证据缺失的输出必须被校验层拒绝，不得静默入库 |

---

## 2. 数据流与 ID 规则

```
文件上传 → A 生成 source_id 并写入 world_sources、原文件存 OBS
        → B 调用 OCR / MaaS，输出本 Schema JSON
        → B 的 Schema 校验层校验（第 11 节）
        → A 的入库服务映射为实体 / 事实 / 事件 / 任务
        → C 前端展示事实、来源、置信度
```

ID 约定（本 Schema 只产出**文档内临时 ID**，最终主键由数据库生成）：

| 字段 | 规则 | 是否入库 |
| --- | --- | --- |
| `source_id` | 由 A 的上传接口返回的雪花数字字符串（如 `1948123456789012345`）；抽取阶段**只透传，禁止自造 UUID** | 是，`world_sources.id`（BIGINT） |
| `entity_id` | 文档内临时 ID，格式 `ent_<序号>`，如 `ent_1` | 否，映射为 `world_entities.id` |
| `fact_id` | 文档内临时 ID，格式 `fact_<序号>` | 否 |
| `event_id` | 文档内临时 ID，格式 `event_<序号>` | 否 |
| `task_id` | 文档内临时 ID，格式 `task_<序号>` | 否 |

> `source_id` 缺失时整份输出判为**非法**，不进库（对应错误码 `E-NO-SOURCE`）。

---

## 3. 顶层输出结构

```json
{
  "schema_version": "1.0",
  "extraction_id": "3f6c2d8e-1a4b-4c9d-9e2f-7a8b9c0d1e2f",
  "source": {},
  "language": "zh-CN",
  "document_type": "invoice",
  "entities": [],
  "facts": [],
  "events": [],
  "tasks": [],
  "warnings": [],
  "extracted_at": "2026-11-21T10:30:00Z"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | 本 Schema 版本，当前固定 `"1.0"` |
| `extraction_id` | string(uuid) | 是 | 单次抽取任务 ID，用于重试、日志追踪 |
| `source` | object | 是 | 来源文件信息，见 4.1 |
| `language` | string | 是 | BCP 47，中文 `zh-CN`；多语言文档取主体语言 |
| `document_type` | string(enum) | 是 | `invoice` / `subscription` / `study_plan` / `warranty` / `contract` / `receipt` / `other` |
| `entities` | array | 是 | 可为空数组，不得为 null |
| `facts` | array | 是 | 可为空数组，不得为 null |
| `events` | array | 是 | 可为空数组，不得为 null |
| `tasks` | array | 是 | 可为空数组，不得为 null |
| `warnings` | array | 是 | 可为空数组，不得为 null |
| `extracted_at` | string(date-time) | 是 | ISO 8601 UTC，如 `2026-11-21T10:30:00Z` |

> 所有数组字段**必须存在**，空结果用 `[]` 表达；缺失字段判 `E-SCHEMA`。

---

## 4. 字段说明

### 4.1 `source`（来源文件）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source_id` | string(snowflake) | 是 | A 上传接口返回的雪花数字字符串，透传 |
| `file_name` | string | 是 | 原始文件名，如 `发票_京东_2026.pdf` |
| `file_type` | string(enum) | 是 | `pdf` / `image` / `txt` |
| `mime_type` | string | 是 | 如 `application/pdf`、`image/jpeg`、`text/plain` |
| `obs_key` | string | 是 | OBS 对象键，不返回公网 URL |
| `sha256` | string | 是 | 原文件摘要，用于去重与防篡改 |
| `page_count` | integer | 否 | PDF 页数；图片/TXT 为 `1` |
| `uploaded_at` | string(date-time) | 是 | 上传时间 |

### 4.2 `entities`（实体）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `entity_id` | string | 是 | 文档内临时 ID，`ent_1` 起 |
| `entity_type` | string(enum) | 是 | 见 5.1 |
| `name` | string | 是 | 规范化名称，如「联想拯救者 Y9000P」 |
| `aliases` | string[] | 否 | 原文中的其他称呼 |
| `attributes` | object | 否 | 实体扩展属性（如 `brand`、`model`、`serial_number`），键必须小驼峰 |
| `confidence` | number | 是 | 0–1，见第 6 节 |
| `source_id` | string(snowflake) | 是 | A 上传接口返回的雪花数字字符串，透传 |
| `evidence` | object | 是 | 见 4.6 |

### 4.3 `facts`（事实，入库核心）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fact_id` | string | 是 | 文档内临时 ID，`fact_1` 起 |
| `subject_ref` | string | 是 | 实体临时 ID（`ent_1`）或临时实体名；入库时换成真实 `subject_id` |
| `predicate` | string(enum) | 是 | 受控谓词，见 5.2；**禁止自由造词** |
| `object_value` | string \| number \| boolean | 是 | 值本体，类型由 predicate 决定 |
| `value_type` | string(enum) | 是 | `string` / `number` / `date` / `datetime` / `duration` / `boolean` |
| `unit` | string | 否 | 金额 `CNY`、时长 `day` / `month` / `year`；仅 number 允许 |
| `normalized_value` | string | 否 | 归一化后的展示/检索值，如 `2026-03-15`、`P1Y` |
| `confidence` | number | 是 | 0–1，见第 6 节 |
| `fact_status` | string(enum) | 是 | `confirmed`（原文直接得出）/ `inferred`（模型推断）/ `unverified`（未验证，A 侧人工录入态，B 不主动产出）；与 A 的 `world_facts.fact_status` 三态对齐 |
| `valid_from` | string(date) | 否 | 事实生效日，规则见 5.2 |
| `valid_until` | string(date) | 否 | 事实失效日；开放区间为 `null` |
| `source_id` | string(snowflake) | 是 | A 上传接口返回的雪花数字字符串，透传 |
| `evidence` | object | 是 | 见 4.6 |

### 4.4 `events`（事件）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `event_id` | string | 是 | 文档内临时 ID |
| `event_type` | string(enum) | 是 | 见 5.3 |
| `title` | string | 是 | 事件标题，如「购买笔记本电脑」 |
| `start_at` | string(date-time) | 否 | 事件开始时间 |
| `end_at` | string(date-time) | 否 | 事件结束时间 |
| `due_at` | string(date-time) | 否 | 到期/截止时间 |
| `related_entity_refs` | string[] | 否 | 关联的 `entity_id` / `fact_id` |
| `confidence` | number | 是 | 0–1 |
| `source_id` | string(snowflake) | 是 | A 上传接口返回的雪花数字字符串，透传 |
| `evidence` | object | 是 | 见 4.6 |

### 4.5 `tasks`（资料中识别出的待办）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `task_id` | string | 是 | 文档内临时 ID |
| `title` | string | 是 | 如「提交课程设计初稿」 |
| `due_at` | string(date-time) | 否 | 截止时间 |
| `priority` | string(enum) | 否 | `high` / `medium` / `low`，缺省 `medium` |
| `related_entity_refs` | string[] | 否 | 关联实体/事实 |
| `confidence` | number | 是 | 0–1 |
| `source_id` | string(snowflake) | 是 | A 上传接口返回的雪花数字字符串，透传 |
| `evidence` | object | 是 | 见 4.6 |

> 这里的 `tasks` 是**资料里明确写出的任务**（如学习计划），不等于 Agent 生成的待办。Agent 待办由 `agent_runs` / `agent_action_logs` 记录。

### 4.6 `evidence`（原文证据）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `text_snippet` | string | 是 | 原文片段，≤ 200 字，禁止模型改写 |
| `page` | integer | 否 | 页码，从 1 开始；图片/TXT 为 1 |
| `bbox` | number[4] | 否 | 图片/PDF 区域 `[x1,y1,x2,y2]`，归一化到 0–1 |
| `char_start` | integer | 否 | TXT 字符偏移起点 |
| `char_end` | integer | 否 | TXT 字符偏移终点 |

> `text_snippet` 为唯一必填证据字段。无法给出原文片段的输出，`confidence` 最高只允许 0.49，按 6.3 不入库。

### 4.7 `warnings`（告警）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string(enum) | 是 | 见第 8 节 |
| `message` | string | 是 | 中文说明 |
| `related_ref` | string | 否 | 关联的临时 ID |

---

## 5. 受控词表

### 5.1 `entity_type`

| 值 | 说明 | 典型 predicate |
| --- | --- | --- |
| `device` | 设备/硬件 | `brand`、`model`、`serial_number`、`purchased_at`、`warranty_until` |
| `subscription` | 订阅服务 | `subscription_cycle`、`subscription_renewal_at`、`auto_renew` |
| `goal` | 目标 | `goal_target`、`goal_deadline` |
| `task` | 任务 | `task_due_at`、`task_status` |
| `course` | 课程 | `course_name`、`course_schedule` |
| `organization` | 机构/商家 | `merchant_name` |
| `person` | 联系人 | `person_role` |
| `account` | 账户 | `account_name` |
| `asset` | 其他资产 | `price`、`location` |
| `project` | 项目 | `project_deadline` |
| `other` | 其他 | — |

### 5.2 `predicate`（受控谓词表 + 入库映射）

`value_type` 为该谓词允许的 `object_value` 类型；`valid_from / valid_until` 为入库时的区间规则。

| predicate | 适用 entity_type | value_type | 单位/格式 | valid_from | valid_until |
| --- | --- | --- | --- | --- | --- |
| `name` | 全部 | string | — | 文档日期或空 | `null` |
| `purchased_at` | device / asset | date | `YYYY-MM-DD` | 购买日 | `null` |
| `price` | device / asset | number | `unit=CNY`，两位小数 | 购买日 | `null` |
| `currency` | device / asset | string | ISO 4217 | 同 price | `null` |
| `merchant_name` | organization | string | — | 购买日 | `null` |
| `brand` | device | string | — | 购买日 | `null` |
| `model` | device | string | — | 购买日 | `null` |
| `serial_number` | device | string | — | 购买日 | `null` |
| `order_id` | device / asset | string | — | 购买日 | `null` |
| `warranty_period` | device | duration | `unit=month` / `year` | 购买日 | 购买日 + 期长 |
| `warranty_until` | device | date | `YYYY-MM-DD` | 购买日 | 保修截止日 |
| `subscription_cycle` | subscription | string(enum) | `monthly` / `quarterly` / `yearly` | 订阅开始日 | 订阅结束日 |
| `subscription_start_at` | subscription | date | `YYYY-MM-DD` | 开始日 | 结束日 |
| `subscription_renewal_at` | subscription | date | `YYYY-MM-DD` | 开始日 | 结束日 |
| `subscription_end_at` | subscription | date | `YYYY-MM-DD` | 开始日 | 结束日 |
| `subscription_price` | subscription | number | `unit=CNY` | 开始日 | 结束日 |
| `auto_renew` | subscription | boolean | — | 开始日 | 结束日 |
| `goal_target` | goal | string | — | 目标设定日 | `goal_deadline` |
| `goal_deadline` | goal | date | `YYYY-MM-DD` | 目标设定日 | 截止日 |
| `course_name` | course | string | — | 开课日 | 结课日 |
| `course_schedule` | course | string | 如「每周二 19:00」 | 开课日 | 结课日 |
| `task_due_at` | task | datetime | ISO 8601 | 任务创建日 | 截止时间 |
| `task_status` | task | string(enum) | `pending` / `doing` / `done` | 任务创建日 | `null` |
| `project_deadline` | project | date | `YYYY-MM-DD` | 项目开始日 | 截止日 |
| `location` | asset / device | string | — | 文档日期或空 | `null` |
| `budget_limit` | person / account | number | `unit=CNY`，**月度**预算总额 | 文档日期或空 | `null` |

**规则**：

1. 谓词只能取上表值；新增谓词必须先改本文档并发 PR（B 主笔、A review），禁止模型运行时自由扩展。
2. `predicate=warranty_until` 与 `warranty_period` 二选一优先：原文给出截止日则用 `warranty_until`；只给「保修 X 年」则用 `warranty_period`，由入库层换算 `valid_until`。
3. 订阅类事实必须至少包含 `subscription_renewal_at` 或 `subscription_end_at` 之一，否则 `W-INCOMPLETE-SUBSCRIPTION`。
4. 金额必须拆成 `price`（number）+ `currency`（string）两条事实，禁止把「¥5999」塞进单个字符串。
5. `budget_limit` 只抽取原文给出的**月度预算总额**；「是否超支」由规则引擎按订阅价格折算判断，模型不得输出预算结论。

### 5.3 `event_type`

| 值 | 说明 | 必填时间字段 |
| --- | --- | --- |
| `purchase` | 购买 | `start_at` |
| `subscription_start` | 订阅开始 | `start_at` |
| `subscription_renewal` | 订阅续费 | `due_at` 或 `start_at` |
| `subscription_expiry` | 订阅到期 | `due_at` |
| `warranty_expiry` | 保修到期 | `due_at` |
| `deadline` | 截止日期 | `due_at` |
| `payment` | 支付 | `start_at` |
| `class` | 上课 | `start_at` / `end_at` |
| `exam` | 考试 | `start_at` |
| `other` | 其他 | 至少一个时间字段 |

> 事件是「时间轴上的点」，事实是「主体的属性」。如「保修到 2027-10-01」应产出一条 `warranty_until` 事实 + 一条 `warranty_expiry` 事件（`due_at=2027-10-01`）。

---

## 6. 置信度规则

### 6.1 取值范围与使用

`confidence` ∈ `[0, 1]`，保留两位小数。**模型自报分数只能作为上限参考**，最终分数由校验层按 6.2 计算。

| 区间 | 等级 | 处理 |
| --- | --- | --- |
| `0.90–1.00` | 高 | 直接入库，正常展示 |
| `0.70–0.89` | 中 | 入库，前端标注「中置信度」 |
| `0.50–0.69` | 低 | 入库，前端标注「待确认」，进入人工复核队列 |
| `< 0.50` | 不可用 | **不入库**，转 `warnings` 记录 |

### 6.2 计算方式（规则化，可复现）

```
confidence = base_score
           × evidence_factor
           × normalization_factor

base_score          原文明确字段（有标签/表头）= 1.00；正文可读但无标签 = 0.90；跨段推断 = 0.75
evidence_factor     有 text_snippet 且定位成功 = 1.00；仅有 text_snippet 无定位 = 0.95；无原文片段 = 0.40
normalization_factor 日期/金额/单位均可归一 = 1.00；部分归一 = 0.90；无法归一 = 0.70
```

### 6.3 强制规则

1. 无 `evidence.text_snippet` → 结果上限 0.49，按不可用处理。
2. `fact_status=inferred` → 最终分再乘 0.85。
3. 同一 `subject_ref + predicate + valid_from` 出现多条冲突值 → 全部降 0.10，并输出 `W-CONFLICT-VALUE`。
4. 日期歧义（如 `03/04/2026` 无法判定日月顺序）→ 上限 0.60，输出 `W-AMBIGUOUS-DATE`。
5. 多页文档中同一字段出现多次且一致 → 可加 0.03，最高不超过 1.00。

---

## 7. 日期、金额与时长归一化

| 类型 | 规则 | 示例 |
| --- | --- | --- |
| 日期 | `YYYY-MM-DD`；中文「2026年3月15日」→ `2026-03-15` | `2026-03-15` |
| 日期时间 | ISO 8601，未知时区按 `+08:00` 处理并输出 `W-TZ-ASSUMED` | `2026-03-15T19:00:00+08:00` |
| 金额 | 去千分位与货币符号，保留两位小数，`unit` 写币种 | `¥5,999` → `5999.00` + `CNY` |
| 时长 | 统一换算为 `day` / `month` / `year`，一年按 365 天、一月按 30 天仅用于排序，不用于精确到期计算 | 「两年保修」→ `24` + `month` |
| 周期 | `monthly` / `quarterly` / `yearly` | 「每月」→ `monthly` |
| 布尔 | 「是/支持/自动续费」→ `true`；「否/不支持」→ `false` | — |

**日期优先级**：原文明确日期 > 由周期推算 > 文档上传日期。推算得到的事实 `fact_status=inferred`，置信度乘 0.85，并输出 `W-DATE-INFERRED`。

---

## 8. 错误码与降级策略

| code | 级别 | 触发条件 | 处理 |
| --- | --- | --- | --- |
| `E-SCHEMA` | 阻断 | 输出不符合第 9 节 JSON Schema | 整份输出拒绝入库，重试一次；仍失败则转人工 |
| `E-NO-SOURCE` | 阻断 | 缺少 `source_id` 或 `source_id` 无对应 `world_sources` 记录 | 整份输出拒绝 |
| `E-NO-EVIDENCE` | 阻断 | fact / event / task 缺少 `evidence.text_snippet` | 该条拒绝，其余照常 |
| `E-LOW-CONF` | 阻断 | `confidence < 0.50` | 该条不入库，写入 `warnings` |
| `E-PREDICATE` | 阻断 | `predicate` 不在 5.2 受控词表 | Parser 丢弃该条并记 warning；Provider 对外抛 90013 整份拒绝；不自动映射近似词 |
| `W-MISSING-FIELD` | 告警 | 关键字段缺失（如订阅无到期日） | 入库，前端提示 |
| `W-AMBIGUOUS-DATE` | 告警 | 日期格式存在日月歧义 | 按第 6.3 节降置信度 |
| `W-CONFLICT-VALUE` | 告警 | 同主体同谓词出现冲突值 | 全部降置信度，保留冲突记录 |
| `W-DATE-INFERRED` | 告警 | 日期由周期推算 | `fact_status=inferred` |
| `W-TZ-ASSUMED` | 告警 | 时间无时区，按 `+08:00` 处理 | 记录假设 |
| `W-UNSUPPORTED-FILE` | 告警 | 文件类型不在 PDF / 图片 / TXT | 不抽取，返回空结果 |
| `W-INCOMPLETE-SUBSCRIPTION` | 告警 | 订阅缺少续费日与到期日 | 入库，风险引擎跳过该条 |
| `W-NO-ENTITY` | 告警 | 事实的 `subject_ref` 无法解析到实体 | 该事实不入库 |
| `W-INVALID-FACT-STATUS` | 告警 | `fact_status` 不在 `confirmed/inferred/unverified` 三态内 | 降级为 `unverified` 后入库，避免 DB CHECK 违规 |

重试策略：`E-SCHEMA` 允许带错误信息重试 1 次；`E-PREDICATE` 不重试，直接拒绝，避免模型「凑词」。

**B 侧 BizException 映射（对齐 A 的 `ResultCode.java`）**：

| ResultCode | code | B 侧触发点 |
| --- | --- | --- |
| `EXTRACTION_NO_SOURCE` | 90011 | `DefaultExtractionProvider.extract()`：`sourceId` 为 null / 空白（A 上传接口返回值，禁止自造 UUID） |
| `EXTRACTION_SCHEMA` | 90012 | `DefaultExtractionProvider.extract()`：`ExtractionSchemaException` 重试耗尽后抛出 |
| `EXTRACTION_PREDICATE` | 90013 | `DefaultExtractionProvider.extract()`：Parser 输出含 `E-PREDICATE` warning 时抛出（不重试） |

分层说明：`ExtractionOutputParser` 面向调试 / Demo，逐条丢弃非法谓词并保留 warning；`DefaultExtractionProvider` 作为对 A 的正式 Provider 契约，汇总后抛 90013，禁止非法谓词静默入库。

---

## 9. JSON Schema（正式定义，draft 2020-12）

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://nexus.local/schemas/extraction-v1.json",
  "title": "NEXUS Extraction Result v1",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "schema_version", "extraction_id", "source", "language",
    "document_type", "entities", "facts", "events", "tasks",
    "warnings", "extracted_at"
  ],
  "properties": {
    "schema_version": { "const": "1.0" },
    "extraction_id": { "type": "string", "format": "uuid" },
    "source": { "$ref": "#/$defs/source" },
    "language": { "type": "string", "pattern": "^[a-z]{2}(-[A-Z]{2})?$" },
    "document_type": { "$ref": "#/$defs/documentType" },
    "entities": { "type": "array", "items": { "$ref": "#/$defs/entity" } },
    "facts": { "type": "array", "items": { "$ref": "#/$defs/fact" } },
    "events": { "type": "array", "items": { "$ref": "#/$defs/event" } },
    "tasks": { "type": "array", "items": { "$ref": "#/$defs/task" } },
    "warnings": { "type": "array", "items": { "$ref": "#/$defs/warning" } },
    "extracted_at": { "type": "string", "format": "date-time" }
  },
  "$defs": {
    "documentType": {
      "type": "string",
      "enum": ["invoice", "subscription", "study_plan", "warranty", "contract", "receipt", "other"]
    },
    "uuid": { "type": "string", "format": "uuid" },
    "snowflakeId": { "type": "string", "pattern": "^[1-9][0-9]{17,18}$" },
    "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
    "date": { "type": "string", "format": "date" },
    "dateTime": { "type": "string", "format": "date-time" },
    "evidence": {
      "type": "object",
      "additionalProperties": false,
      "required": ["text_snippet"],
      "properties": {
        "text_snippet": { "type": "string", "minLength": 1, "maxLength": 200 },
        "page": { "type": "integer", "minimum": 1 },
        "bbox": {
          "type": "array",
          "items": { "type": "number", "minimum": 0, "maximum": 1 },
          "minItems": 4,
          "maxItems": 4
        },
        "char_start": { "type": "integer", "minimum": 0 },
        "char_end": { "type": "integer", "minimum": 0 }
      }
    },
    "source": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "source_id", "file_name", "file_type", "mime_type",
        "obs_key", "sha256", "uploaded_at"
      ],
      "properties": {
        "source_id": { "$ref": "#/$defs/snowflakeId" },
        "file_name": { "type": "string", "minLength": 1 },
        "file_type": { "type": "string", "enum": ["pdf", "image", "txt"] },
        "mime_type": { "type": "string", "minLength": 1 },
        "obs_key": { "type": "string", "minLength": 1 },
        "sha256": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
        "page_count": { "type": "integer", "minimum": 1 },
        "uploaded_at": { "$ref": "#/$defs/dateTime" }
      }
    },
    "entity": {
      "type": "object",
      "additionalProperties": false,
      "required": ["entity_id", "entity_type", "name", "confidence", "source_id", "evidence"],
      "properties": {
        "entity_id": { "type": "string", "pattern": "^ent_[0-9]+$" },
        "entity_type": {
          "type": "string",
          "enum": ["device", "subscription", "goal", "task", "course",
                   "organization", "person", "account", "asset", "project", "other"]
        },
        "name": { "type": "string", "minLength": 1 },
        "aliases": { "type": "array", "items": { "type": "string" } },
        "attributes": { "type": "object", "additionalProperties": true },
        "confidence": { "$ref": "#/$defs/confidence" },
        "source_id": { "$ref": "#/$defs/snowflakeId" },
        "evidence": { "$ref": "#/$defs/evidence" }
      }
    },
    "fact": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "fact_id", "subject_ref", "predicate", "object_value", "value_type",
        "confidence", "fact_status", "source_id", "evidence"
      ],
      "properties": {
        "fact_id": { "type": "string", "pattern": "^fact_[0-9]+$" },
        "subject_ref": { "type": "string", "minLength": 1 },
        "predicate": {
          "type": "string",
          "enum": [
            "name", "purchased_at", "price", "currency", "merchant_name",
            "brand", "model", "serial_number", "order_id",
            "warranty_period", "warranty_until",
            "subscription_cycle", "subscription_start_at", "subscription_renewal_at",
            "subscription_end_at", "subscription_price", "auto_renew",
            "goal_target", "goal_deadline", "course_name", "course_schedule",
            "task_due_at", "task_status", "project_deadline", "location", "budget_limit"
          ]
        },
        "object_value": { "type": ["string", "number", "boolean"] },
        "value_type": {
          "type": "string",
          "enum": ["string", "number", "date", "datetime", "duration", "boolean"]
        },
        "unit": { "type": "string", "enum": ["CNY", "USD", "EUR", "day", "month", "year"] },
        "normalized_value": { "type": "string" },
        "confidence": { "$ref": "#/$defs/confidence" },
        "fact_status": { "type": "string", "enum": ["confirmed", "inferred", "unverified"] },
        "valid_from": { "$ref": "#/$defs/date" },
        "valid_until": { "type": ["string", "null"], "format": "date" },
        "source_id": { "$ref": "#/$defs/snowflakeId" },
        "evidence": { "$ref": "#/$defs/evidence" }
      }
    },
    "event": {
      "type": "object",
      "additionalProperties": false,
      "required": ["event_id", "event_type", "title", "confidence", "source_id", "evidence"],
      "properties": {
        "event_id": { "type": "string", "pattern": "^event_[0-9]+$" },
        "event_type": {
          "type": "string",
          "enum": ["purchase", "subscription_start", "subscription_renewal",
                   "subscription_expiry", "warranty_expiry", "deadline",
                   "payment", "class", "exam", "other"]
        },
        "title": { "type": "string", "minLength": 1 },
        "start_at": { "$ref": "#/$defs/dateTime" },
        "end_at": { "$ref": "#/$defs/dateTime" },
        "due_at": { "$ref": "#/$defs/dateTime" },
        "related_entity_refs": { "type": "array", "items": { "type": "string" } },
        "confidence": { "$ref": "#/$defs/confidence" },
        "source_id": { "$ref": "#/$defs/snowflakeId" },
        "evidence": { "$ref": "#/$defs/evidence" }
      }
    },
    "task": {
      "type": "object",
      "additionalProperties": false,
      "required": ["task_id", "title", "confidence", "source_id", "evidence"],
      "properties": {
        "task_id": { "type": "string", "pattern": "^task_[0-9]+$" },
        "title": { "type": "string", "minLength": 1 },
        "due_at": { "$ref": "#/$defs/dateTime" },
        "priority": { "type": "string", "enum": ["high", "medium", "low"] },
        "related_entity_refs": { "type": "array", "items": { "type": "string" } },
        "confidence": { "$ref": "#/$defs/confidence" },
        "source_id": { "$ref": "#/$defs/snowflakeId" },
        "evidence": { "$ref": "#/$defs/evidence" }
      }
    },
    "warning": {
      "type": "object",
      "additionalProperties": false,
      "required": ["code", "message"],
      "properties": {
        "code": {
          "type": "string",
          "enum": [
            "E-SCHEMA", "E-NO-SOURCE", "E-NO-EVIDENCE", "E-LOW-CONF", "E-PREDICATE",
            "W-MISSING-FIELD", "W-AMBIGUOUS-DATE", "W-CONFLICT-VALUE", "W-DATE-INFERRED",
            "W-TZ-ASSUMED", "W-UNSUPPORTED-FILE", "W-INCOMPLETE-SUBSCRIPTION", "W-NO-ENTITY"
          ]
        },
        "message": { "type": "string", "minLength": 1 },
        "related_ref": { "type": "string" }
      }
    }
  }
}
```

> 注意：JSON Schema 的 `type` 同时允许 `string`/`number`/`boolean` 时，校验层需额外按 5.2 的 `value_type` 做交叉校验（Schema 本身无法表达 predicate 与 value_type 的联动）。
> `format: date-time` 要求 RFC 3339；`valid_until` 为 `null` 表示开放区间。

---

## 10. 与数据库表的映射

### 10.1 `world_sources`

| 抽取字段 | 表字段 |
| --- | --- |
| `source.source_id` | `id` |
| `source.file_name` | `file_name` |
| `source.file_type` | `file_type` |
| `source.mime_type` | `mime_type` |
| `source.obs_key` | `obs_key` |
| `source.sha256` | `sha256` |
| `source.page_count` | `page_count` |
| `source.uploaded_at` | `uploaded_at` |

### 10.2 `world_entities`

| 抽取字段 | 表字段 |
| --- | --- |
| `entity_id` | 入库后新生成 `id`，临时 ID 存映射表 |
| `entity_type` | `entity_type` |
| `name` | `name` |
| `aliases` | `aliases`（JSONB） |
| `attributes` | `attributes`（JSONB） |
| `confidence` | `confidence` |
| `source_id` | `source_id` |

### 10.3 `world_facts`（核心）

| 抽取字段 | 表字段 |
| --- | --- |
| `subject_ref` | `subject_id`（经实体映射表转换） |
| `predicate` | `predicate` |
| `object_value` | `object_value`（A 定稿为 TEXT，number/boolean 落库前序列化为字符串） |
| `value_type` | `value_type`（阶段二已补列） |
| `unit` | `unit`（阶段二已补列） |
| `normalized_value` | `normalized_value`（阶段二已补列） |
| `confidence` | `confidence` |
| `fact_status` | `fact_status`（阶段二已补列，三态 confirmed/inferred/unverified） |
| `evidence` | `evidence`（阶段二已补列，TEXT 存 JSON） |
| `valid_from` | `valid_from` |
| `valid_until` | `valid_until` |
| `source_id` | `source_id` |
| `evidence` | 数据库暂无证据列，随 `extra.evidence` 透传（textSnippet/page） |
| — | `user_id`（由服务端从 JWT 注入，**禁止来自抽取结果**） |
| — | `created_at` / `updated_at`（数据库生成） |

### 10.4 `world_events` / `tasks`

`event_id` / `task_id` 为临时 ID，入库生成真实主键；`related_entity_refs` 需先解析为真实 ID，解析失败的关联丢弃并记 `W-NO-ENTITY`。`source_id`、`confidence`、`evidence` 同样透传。

**events 入库细则（2026-11-23 拍板，A 侧实施）**：

1. `event_type` 10→6 映射（2026-11-23 已与 B 契约 `f60bb4d` 复核）：`purchase`→`asset`；`payment`/`subscription_start`/`subscription_renewal`/`subscription_expiry`→`contract`；`warranty_expiry`→`asset`；`class`/`exam`→`education`；`deadline`/`other`→`other`。`deadline` 按关联实体细化留后续 PR。
2. `occurred_at` 按 `start_at` > `due_at` > `end_at` 取值，统一归一 UTC；三者全缺失则该条跳过并记 `W-MISSING-FIELD`，不写 `Instant.now()`。
3. 幂等：应用层按 `(user_id, source_id, title, occurred_at)` 查重，不加 DDL；`event_id` 仅作文档内临时键。
4. `evidence.text_snippet` 写 `world_events.description`（≤200 字），`confidence < 0.50` 丢弃且不落库（`world_events` 无该列）。
5. `related_entity_refs` 仅解析 `ent_*` 引用；`world_events.entity_id` 为单列，多实体关联取第一个成功解析者。
6. `tasks` 本轮只定结构、透传前端展示，不自动落库；`tasks.source_type` DDL 已预留 `extracted`，自动落库须经 C 的 UI 确认后另开 PR。

### 10.5 AI DTO `extra` 键约定（与 A 的 `ExtractionRequest.ExtractedFact` 对齐）

A 的 `ExtractionRequest.ExtractedFact` 只有 `subjectType / subjectName / predicate / objectValue / confidence / extra`，完整抽取信息通过 `extra` 透传。键名固定如下，实现方（B）与入库方（A）均不得运行时改名。**2026-11-23 起以 snake_case 为规范键；camelCase 仅 A 侧 Parser/管线临时兼容读取，端到端验收后移除。**

| `extra` 键 | 类型 | 来源 | 入库处理 |
| --- | --- | --- | --- |
| `source_id` | string(snowflake) | 本 Schema 的 `source.source_id`，来自 A 上传接口返回值（B 只透传）；Demo 由 `nexus.ai.demo.source-id` 注入演示值 | 写 `world_facts.source_id`（BIGINT 外键，需 A 先落 `world_sources`） |
| `entity_id` | string | 文档内临时实体 ID，如 `ent_1` | 不入库，仅用于关联 |
| `entity_type` | string | 本 Schema 的 `entity_type` | 按 10.6 映射为 `world_entities.entity_type` |
| `value_type` | string | `fact.value_type` | 写 `world_facts.value_type`（阶段二已补列） |
| `unit` | string \| null | `fact.unit` | 写 `world_facts.unit`（阶段二已补列） |
| `normalized_value` | string \| null | `fact.normalized_value` | 写 `world_facts.normalized_value`（阶段二已补列） |
| `fact_status` | string | `confirmed` / `inferred` / `unverified`（见 4.3 节映射） | 写 `world_facts.fact_status`（阶段二已补列） |
| `valid_from` | string \| null | `YYYY-MM-DD` | 转 TIMESTAMP 写 `world_facts.valid_from` |
| `valid_until` | string \| null | `YYYY-MM-DD` | 转 TIMESTAMP 写 `world_facts.valid_until` |
| `evidence` | object | `{ text_snippet, page }` | 序列化写 `world_facts.evidence`（阶段二已补列，TEXT 存 JSON） |

> `confidence < 0.50` 的事实已在 B 侧过滤，不会出现在 DTO 中；A 侧入库前只需再校验 `source_id` 与 `subject_id` 映射。

### 10.6 `entity_type` 映射（本 Schema → `world_entities.entity_type`）

| 本 Schema `entity_type` | A 的 `world_entities.entity_type` |
| --- | --- |
| `device` / `subscription` / `account` / `asset` | `asset` |
| `organization` | `org` |
| `person` | `person` |
| `goal` / `task` / `course` / `project` / `other` | `event` |

> A 的枚举无 `document`、`other`，因此本 Schema 的 `other` 统一落为 `event`，避免触发 DDL CHECK 约束失败。

---

## 11. 校验与入库流程

```
1. JSON 解析             失败 → E-SCHEMA，拒绝
2. JSON Schema 校验      失败 → E-SCHEMA，重试 1 次
3. source_id 存在性校验  失败 → E-NO-SOURCE，拒绝
4. predicate 词表校验    失败 → E-PREDICATE，该条拒绝
5. predicate × value_type 联动校验  失败 → 该条拒绝并记 W-MISSING-FIELD
6. 证据完整性校验        缺 text_snippet → E-NO-EVIDENCE，该条拒绝
7. 置信度门槛             < 0.50 → E-LOW-CONF，该条转 warnings
8. subject_ref 实体解析   失败 → W-NO-ENTITY，该条不入库
9. 冲突检测               同主体同谓词多值 → W-CONFLICT-VALUE，降分
10. 入库                  entities → facts → events → tasks，同一事务
11. 回写                  extraction_id 与入库结果写抽取日志
```

幂等要求：同一 `source_id + sha256` 重复抽取时，先删除旧的该来源抽取结果再写入；或按 `(source_id, subject_id, predicate, valid_from)` 做 upsert（见待确认项 4）。

---

## 12. 与 A 的 `database-design.md` 对齐结果（2026-11-21）

已对照 A 的阶段一定稿候选 DDL 逐条核对；**2026-11-22 更新**：A 在 `a6e5c92`（PR #1 review 拍板）中已落实多项差异，下表结论同步为最新。

| # | 问题 | 定稿结论 / 现状 | 影响与处理 |
| --- | --- | --- | --- |
| 1 | `world_facts.object_value` 用 JSONB 还是 TEXT | **TEXT** | number/boolean 落库前序列化为字符串 |
| 2 | `world_entities.attributes` / `aliases` 是否支持 JSONB | `attributes` 为 JSONB；**无 `aliases` 列** | aliases 暂并入 attributes 或后续加列（未变更） |
| 3 | `world_facts` 是否保留 `value_type` / `unit` / `normalized_value` / `fact_status` | **阶段二已补列**（`a6e5c92`） | B 的 `extra` 字段现在可直接落库，不再仅透传 |
| 4 | 事实唯一键与去重策略 | DDL 未声明唯一约束 | 应用层按 `(user_id, source_id, subject_id, predicate, valid_from)` 判重 |
| 5 | `world_sources` 是否含 `sha256` / `page_count` / `uploaded_at` | **阶段二已补列**（`a6e5c92`） | 去重字段已落库 |
| 6 | `evidence` 存 `world_facts` JSONB 还是独立证据表 | **阶段二已补 `evidence` 列（TEXT 存 JSON）** | 随 `extra` 透传后写列，不再仅透传 |
| 7 | 实体跨文档合并策略 | DDL 无唯一约束，A 文档约定按 `(user_id, entity_type, name)` 判重 | 待联调确认（未变更） |
| 8 | `source_id` 生成时机与类型 | A 上传服务生成**雪花数字字符串**（BIGINT，非 UUID）；B 只透传，禁止自造 | 见 4.1 节与 ID 约定；B 的 `source_id` 已由 uuid 改为 snowflake |
| 9 | predicate 是否落字典表 | DDL 为 VARCHAR，无字典表 | 保持代码枚举（5.2 词表） |
| 10 | `world_risks` 是否需要引用 `fact_id` 列表 | DDL 无事实关联列 | 风险证据展示需后续扩展（未变更）；`level` 已由 `mid` 统一为 `medium` |
| 11 | `ExtractionRequest` 是否携带 `source_id` | **阶段二已加 `sourceId` 字段**（`a6e5c92`） | P0-1 关闭；B 从 request 直接取 `sourceId` |
| 12 | `source_id` 类型三方冲突（PR review 发现） | **拍板：保持 BIGINT**，B 透传 A 上传接口返回的雪花数字字符串，禁止自造 UUID | 已同步 B 的 schema / Demo / 测试；无需映射表 |

> 原契约差异第 3、5、6、11 项已由 A 在 `a6e5c92` 关闭；第 8/12 项 `source_id` 类型拍板为 BIGINT 雪花数字字符串。仍未决：第 2、7、10 项（aliases / 实体合并 / 风险事实关联），列为 P1 联调确认项。

---

## 13. 示例

### 13.1 笔记本发票（PDF）

```json
{
  "schema_version": "1.0",
  "extraction_id": "0b6f3c1e-2d4a-4e8b-9c7d-1a2b3c4d5e6f",
  "source": {
    "source_id": "1948123456789012345",
    "file_name": "发票_京东_笔记本.pdf",
    "file_type": "pdf",
    "mime_type": "application/pdf",
    "obs_key": "uploads/2026/11/9a8b7c6d/invoice.pdf",
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "page_count": 1,
    "uploaded_at": "2026-11-20T09:00:00Z"
  },
  "language": "zh-CN",
  "document_type": "invoice",
  "entities": [
    {
      "entity_id": "ent_1",
      "entity_type": "device",
      "name": "联想拯救者 Y9000P",
      "aliases": ["Y9000P", "拯救者笔记本"],
      "attributes": { "brand": "联想", "model": "Y9000P 2026" },
      "confidence": 0.96,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "商品名称：联想拯救者Y9000P 2026款", "page": 1 }
    }
  ],
  "facts": [
    {
      "fact_id": "fact_1",
      "subject_ref": "ent_1",
      "predicate": "purchased_at",
      "object_value": "2026-11-15",
      "value_type": "date",
      "normalized_value": "2026-11-15",
      "confidence": 0.95,
      "fact_status": "confirmed",
      "valid_from": "2026-11-15",
      "valid_until": null,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "开票日期：2026-11-15", "page": 1 }
    },
    {
      "fact_id": "fact_2",
      "subject_ref": "ent_1",
      "predicate": "price",
      "object_value": 8999.00,
      "value_type": "number",
      "unit": "CNY",
      "normalized_value": "8999.00",
      "confidence": 0.97,
      "fact_status": "confirmed",
      "valid_from": "2026-11-15",
      "valid_until": null,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "价税合计：¥8,999.00", "page": 1 }
    },
    {
      "fact_id": "fact_3",
      "subject_ref": "ent_1",
      "predicate": "currency",
      "object_value": "CNY",
      "value_type": "string",
      "confidence": 0.99,
      "fact_status": "confirmed",
      "valid_from": "2026-11-15",
      "valid_until": null,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "价税合计：¥8,999.00", "page": 1 }
    },
    {
      "fact_id": "fact_4",
      "subject_ref": "ent_1",
      "predicate": "merchant_name",
      "object_value": "京东商城",
      "value_type": "string",
      "confidence": 0.94,
      "fact_status": "confirmed",
      "valid_from": "2026-11-15",
      "valid_until": null,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "销售方：京东商城", "page": 1 }
    },
    {
      "fact_id": "fact_5",
      "subject_ref": "ent_1",
      "predicate": "warranty_period",
      "object_value": 2,
      "value_type": "duration",
      "unit": "year",
      "normalized_value": "P2Y",
      "confidence": 0.88,
      "fact_status": "inferred",
      "valid_from": "2026-11-15",
      "valid_until": "2028-11-15",
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "保修政策：整机保修两年", "page": 1 }
    }
  ],
  "events": [
    {
      "event_id": "event_1",
      "event_type": "purchase",
      "title": "购买联想拯救者 Y9000P",
      "start_at": "2026-11-15T00:00:00+08:00",
      "related_entity_refs": ["ent_1", "fact_1"],
      "confidence": 0.95,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "开票日期：2026-11-15", "page": 1 }
    },
    {
      "event_id": "event_2",
      "event_type": "warranty_expiry",
      "title": "保修到期",
      "due_at": "2028-11-15T00:00:00+08:00",
      "related_entity_refs": ["ent_1", "fact_5"],
      "confidence": 0.84,
      "source_id": "1948123456789012345",
      "evidence": { "text_snippet": "保修政策：整机保修两年", "page": 1 }
    }
  ],
  "tasks": [],
  "warnings": [
    { "code": "W-DATE-INFERRED", "message": "保修截止日由购买日与保修期推算", "related_ref": "fact_5" }
  ],
  "extracted_at": "2026-11-20T09:01:10Z"
}
```

### 13.2 订阅确认邮件（TXT）

```json
{
  "schema_version": "1.0",
  "extraction_id": "6d5c4b3a-2f1e-4d3c-8b7a-9f8e7d6c5b4a",
  "source": {
    "source_id": "1948123456789012346",
    "file_name": "订阅确认.txt",
    "file_type": "txt",
    "mime_type": "text/plain",
    "obs_key": "uploads/2026/11/1948123456789012346/subscription.txt",
    "sha256": "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
    "page_count": 1,
    "uploaded_at": "2026-11-18T12:00:00Z"
  },
  "language": "zh-CN",
  "document_type": "subscription",
  "entities": [
    {
      "entity_id": "ent_1",
      "entity_type": "subscription",
      "name": "华为云音乐会员",
      "confidence": 0.93,
      "source_id": "1948123456789012346",
      "evidence": { "text_snippet": "您已开通华为云音乐会员", "page": 1 }
    }
  ],
  "facts": [
    {
      "fact_id": "fact_1",
      "subject_ref": "ent_1",
      "predicate": "subscription_start_at",
      "object_value": "2026-11-18",
      "value_type": "date",
      "confidence": 0.92,
      "fact_status": "confirmed",
      "valid_from": "2026-11-18",
      "valid_until": "2026-12-18",
      "source_id": "1948123456789012346",
      "evidence": { "text_snippet": "生效日期：2026-11-18", "page": 1 }
    },
    {
      "fact_id": "fact_2",
      "subject_ref": "ent_1",
      "predicate": "subscription_renewal_at",
      "object_value": "2026-12-18",
      "value_type": "date",
      "confidence": 0.90,
      "fact_status": "confirmed",
      "valid_from": "2026-11-18",
      "valid_until": "2026-12-18",
      "source_id": "1948123456789012346",
      "evidence": { "text_snippet": "下次续费日期：2026-12-18", "page": 1 }
    },
    {
      "fact_id": "fact_3",
      "subject_ref": "ent_1",
      "predicate": "subscription_cycle",
      "object_value": "monthly",
      "value_type": "string",
      "confidence": 0.93,
      "fact_status": "confirmed",
      "valid_from": "2026-11-18",
      "valid_until": "2026-12-18",
      "source_id": "1948123456789012346",
      "evidence": { "text_snippet": "按月订阅", "page": 1 }
    },
    {
      "fact_id": "fact_4",
      "subject_ref": "ent_1",
      "predicate": "auto_renew",
      "object_value": true,
      "value_type": "boolean",
      "confidence": 0.91,
      "fact_status": "confirmed",
      "valid_from": "2026-11-18",
      "valid_until": "2026-12-18",
      "source_id": "1948123456789012346",
      "evidence": { "text_snippet": "已开启自动续费", "page": 1 }
    }
  ],
  "events": [
    {
      "event_id": "event_1",
      "event_type": "subscription_renewal",
      "title": "会员自动续费",
      "due_at": "2026-12-18T00:00:00+08:00",
      "related_entity_refs": ["ent_1", "fact_2"],
      "confidence": 0.90,
      "source_id": "1948123456789012346",
      "evidence": { "text_snippet": "下次续费日期：2026-12-18", "page": 1 }
    }
  ],
  "tasks": [],
  "warnings": [],
  "extracted_at": "2026-11-18T12:01:05Z"
}
```

### 13.3 学习计划（图片 OCR）

```json
{
  "schema_version": "1.0",
  "extraction_id": "8c7b6a5d-4e3f-4a2b-9c1d-0e9f8a7b6c5d",
  "source": {
    "source_id": "1948123456789012347",
    "file_name": "学习计划.png",
    "file_type": "image",
    "mime_type": "image/png",
    "obs_key": "uploads/2026/11/1948123456789012347/plan.png",
    "sha256": "b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef01",
    "page_count": 1,
    "uploaded_at": "2026-11-16T08:00:00Z"
  },
  "language": "zh-CN",
  "document_type": "study_plan",
  "entities": [
    {
      "entity_id": "ent_1",
      "entity_type": "goal",
      "name": "通过华为 ICT 大赛初赛",
      "confidence": 0.90,
      "source_id": "1948123456789012347",
      "evidence": { "text_snippet": "目标：通过华为ICT大赛初赛", "page": 1 }
    },
    {
      "entity_id": "ent_2",
      "entity_type": "task",
      "name": "完成项目原型",
      "confidence": 0.89,
      "source_id": "1948123456789012347",
      "evidence": { "text_snippet": "11月30日前完成项目原型", "page": 1 }
    }
  ],
  "facts": [
    {
      "fact_id": "fact_1",
      "subject_ref": "ent_1",
      "predicate": "goal_deadline",
      "object_value": "2026-12-20",
      "value_type": "date",
      "confidence": 0.86,
      "fact_status": "confirmed",
      "valid_from": "2026-11-16",
      "valid_until": "2026-12-20",
      "source_id": "1948123456789012347",
      "evidence": { "text_snippet": "初赛截止：2026年12月20日", "page": 1 }
    },
    {
      "fact_id": "fact_2",
      "subject_ref": "ent_2",
      "predicate": "task_due_at",
      "object_value": "2026-11-30T23:59:00+08:00",
      "value_type": "datetime",
      "confidence": 0.88,
      "fact_status": "confirmed",
      "valid_from": "2026-11-16",
      "valid_until": "2026-11-30T23:59:00+08:00",
      "source_id": "1948123456789012347",
      "evidence": { "text_snippet": "11月30日前完成项目原型", "page": 1 }
    }
  ],
  "events": [
    {
      "event_id": "event_1",
      "event_type": "deadline",
      "title": "完成项目原型",
      "due_at": "2026-11-30T23:59:00+08:00",
      "related_entity_refs": ["ent_2", "fact_2"],
      "confidence": 0.88,
      "source_id": "1948123456789012347",
      "evidence": { "text_snippet": "11月30日前完成项目原型", "page": 1 }
    }
  ],
  "tasks": [
    {
      "task_id": "task_1",
      "title": "完成项目原型",
      "due_at": "2026-11-30T23:59:00+08:00",
      "priority": "high",
      "related_entity_refs": ["ent_2", "fact_2"],
      "confidence": 0.88,
      "source_id": "1948123456789012347",
      "evidence": { "text_snippet": "11月30日前完成项目原型", "page": 1 }
    }
  ],
  "warnings": [
    { "code": "W-TZ-ASSUMED", "message": "截止时间未标注时区，按 +08:00 处理", "related_ref": "fact_2" }
  ],
  "extracted_at": "2026-11-16T08:01:20Z"
}
```

---

## 14. 变更记录

| 版本 | 日期 | 变更 | 作者 |
| --- | --- | --- | --- |
| v0.1 | 2026-11-21 | 首版：顶层结构、实体/事实/事件/任务字段、受控谓词表、置信度规则、JSON Schema、入库映射、示例 | B |
| v0.2 | 2026-11-22 | PR #1 review 对齐：source_id 全量改为雪花数字串（含 13.2/13.3 示例与 obs_key），补 90011-90013 错误码映射，三态 fact_status 确认，level 统一 medium | B |
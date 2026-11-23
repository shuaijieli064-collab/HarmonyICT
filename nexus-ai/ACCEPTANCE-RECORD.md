# AI 引擎验收记录（成员 B）

> 日期：2026-11-23　|　分支：`feature/ai-b`　|　状态：**real 实调待凭据，其余已完成**
> 本文件是答辩证据索引：每条结论都附可复现命令与结果，真实调用日志待补第 4 节。

---

## 1. 总览

| # | 任务 | 状态 | 证据 |
| --- | --- | --- | --- |
| 1 | MaaS/OCR 真实调用验收 | ⛔ 阻塞（本机 6 项凭据未配置，`real-call.env` 不存在） | 执行步骤见第 4 节 |
| 2 | 真实模式全链路回归 + mock Schema 合规对比 | ✅ mock 基线完成；real 待凭据 | 第 3 节 |
| 3 | 4 类风险覆盖度确认 | ✅ 规则层 4/4 通过；2 项链路缺口已定位 | 第 5 节 |
| 4 | 演示资料补充（PDF/图片/TXT） | ✅ 三份已生成 | 第 6 节 |
| 5 | events 字段消费方案 | ✅ 契约文档已产出，待 A review | `EVENTS-INGESTION-CONTRACT.md` |

---

## 2. 环境

- Java 21.0.9 + Maven（`spring-boot:run`）
- 本地 profile：H2 内存库（`application-local.yml`），无需 PostgreSQL/Redis 即可启动
- 凭据检查结果（只输出是否设置，不输出值）：

```
NEXUS_AI_MAAS_API_KEY=EMPTY
NEXUS_AI_MAAS_MODEL=EMPTY
NEXUS_AI_OCR_USERNAME=EMPTY
NEXUS_AI_OCR_PASSWORD=EMPTY
NEXUS_AI_OCR_DOMAIN_NAME=EMPTY
NEXUS_AI_OCR_PROJECT_ID=EMPTY
```

> ⚠️ 环境已知问题：本机 `mvn spring-boot:run` fork 出的 JVM 写入 `nexus-ai/testdata/` 会报
> `AccessDeniedException`（独立 `java` 进程与 bash 均可正常写入）。验收时通过
> `NEXUS_AI_DEMO_OUTPUT` 把输出改到可写目录即可；`verify-real-call.sh` 已支持该变量。

---

## 3. 任务 2：mock / real 配置与 Schema 合规对比

### 3.1 配置对比

| 项 | mock 模式 | real 模式 |
| --- | --- | --- |
| 开关 | `nexus.ai.mode=mock`（或 `NEXUS_AI_MODE=mock`） | `nexus.ai.mode=real`（默认） |
| LLM | `MockLlmProvider` → `RuleBasedExtractor`（确定性正则） | `MaasLlmProvider` → 华为云 MaaS `/v1/chat/completions` |
| OCR | `MockOcrProvider`（只解码纯文本 base64，不识别图片） | `HuaweiOcrProvider`（IAM 鉴权 + 图片/PDF 逐页转图） |
| 输入覆盖 | TXT 可跑；图片/PDF 无有效文本 | TXT / 图片 / PDF |
| 输出 | 与 Prompt 同构的 camelCase JSON | 模型自由输出，经 Parser 校验 facts |
| 失败重试 | 无 | Schema 不符重试（`nexus.ai.extraction.max-retries`）；`90013` 不重试 |

### 3.2 mock 基线执行结果

命令（工作目录 `nexus-backend`）：

```
mvn -q spring-boot:run -Dspring-boot.run.arguments="--nexus.ai.mode=mock --nexus.ai.demo.enabled=true --nexus.ai.demo.input=E:/Data/HarmonyICT/nexus-ai/testdata/subscription.txt --nexus.ai.demo.output=E:/Data/HarmonyICT/nexus-backend/target/extraction-mock-txt.json"
```

结果：`抽取 Demo 完成 provider=mock-rule-based facts=3 overallConfidence=0.91 warnings=[]`

演示资料（含预算）结果：`facts=5`，抽出 `subscription_renewal_at / subscription_cycle / auto_renew / subscription_price / budget_limit`
→ 新增预算谓词链路已生效。

### 3.3 Schema 合规校验（`nexus-ai/schema_check.py`）

```
python nexus-ai/schema_check.py nexus-backend/target/extraction-mock-txt.json --source-id 1948123456789012345
```

结论：**不符合 `docs/extraction-schema.md` 第 9 节 JSON Schema，共 8 项**：

| 违规 | 原因 |
| --- | --- |
| 顶层缺 `extraction_id` | Prompt 与 mock 输出均未包含 |
| 顶层缺 `source` | 抽取阶段 B 拿不到 `file_name/sha256/obs_key`，只能拿到 `sourceId` |
| 顶层缺 `language` | Prompt 未要求 |
| 顶层缺 `extracted_at` | Prompt 未要求 |
| `extraction_id/language/extracted_at` 类型校验失败 | 字段缺失导致 |
| `source` 非对象 | 字段缺失导致 |
| 命名风格 camelCase（`schemaVersion/documentType`） | 第 9 节 Schema 定义的是 snake_case |

**影响评估**：不影响入库——`ExtractionOutputParser` 只校验 `entities/facts`，A 的编排只消费 `facts`。
但"输出符合 Schema"的对外宣称不成立；real 模式使用同一 Prompt，缺陷相同。

**修复建议（待 A review，本次未改契约）**：

1. **推荐**：文档明确线上 wire format 为 camelCase，并给出 camelCase 版 JSON Schema；
   `source` 改为"B 产出 `source_id`，其余由 A 上传时补全"，顶层必填缩减为 B 能产出的字段。
2. 或：Prompt 补齐 `extractionId / language / extractedAt / source.sourceId`，再把文档 Schema 与
   Parser/A 消费端统一改为 camelCase；成本更高，涉及三方同步。

### 3.4 real 全链路回归清单（凭据到位后执行）

1. `cp nexus-ai/real-call.env.template nexus-ai/real-call.env`，填 6 项凭据 + `NEXUS_AI_DEMO_SOURCE_ID`（A 上传接口返回的雪花数字串）；
2. `bash nexus-ai/verify-real-call.sh nexus-ai/real-call.env`（必要时设 `NEXUS_AI_DEMO_OUTPUT`）；
3. 核对三段完成日志：`验收① MaaS 文本抽取`、`验收② OCR 图片识别`、`验收③ PDF 逐页转图`；
4. `python nexus-ai/schema_check.py <输出 JSON> --source-id <sourceId>`，记录与 3.3 相同的顶层缺口是否仍存在；
5. 上传→抽取→落库：上传第 6 节三份资料，轮询 `world_sources.status` 到 `parsed`，
   查 `world_facts` 核对 `source_id` 可追溯、`confidence/factStatus` 未被覆盖为 `1.0/confirmed`；
6. 等待风险扫描（60s 调度）后查 `world_risks`，对照第 5 节预期；
7. 保存 MaaS/OCR 调用日志与请求 ID，作为答辩证据附在本文件第 4 节。

---

## 4. 任务 1：真实调用验收记录（待补）

| 项 | 结果 | 证据 |
| --- | --- | --- |
| MaaS 文本抽取（subscription.txt） | ⏳ 未执行 | — |
| OCR 图片识别（study-plan.png） | ⏳ 未执行 | — |
| PDF 逐页转图 + OCR（invoice.pdf） | ⏳ 未执行 | — |
| 调用日志 / 请求 ID | ⏳ 未采集 | — |

> 凭据到位后在此追加：执行时间、模型名、三段日志摘要、输出 JSON 路径、`schema_check` 结果。

---

## 5. 任务 3：4 类风险覆盖度

### 5.1 规则层验证（可执行证据）

`nexus-backend/src/test/java/com/nexus/ai/risk/RiskCoverageMatrixTest.java`：

```
mvn -q -Dtest=RiskCoverageMatrixTest test
→ tests=4, failures=0, errors=0
```

| 风险类 | ruleCode | 触发条件 | 结果 |
| --- | --- | --- | --- |
| 期限监控 | `DEADLINE_1D`（high） | `task_due_at` = 基准日 +1 天 | ✅ |
| 日程冲突 | `SCHEDULE_CONFLICT_HOUR`（medium） | 同日 10:00 / 10:30 两条事件 | ✅ |
| 日程冲突 | `SCHEDULE_CONFLICT_DAY`（low） | 同日 09:00 / 15:00 两条事件 | ✅ |
| 保修到期 | `WARRANTY_EXPIRY_30D`（low） | `warranty_until` = 基准日 +8 天 | ✅ |
| 订阅到期 | `SUBSCRIPTION_EXPIRY_7D`（medium） | `subscription_renewal_at` = 基准日 +2 天 | ✅ |
| 预算约束 | `BUDGET_OVERSPENT`（high） | 月度 19+39=58 / 预算 50 = 116% | ✅ |

边界用例同时通过：年度订阅按 1/12 折算、79% 不触发、无预算事实不臆测。

### 5.2 真实链路可达性（关键结论）

| 风险类 | 真实数据可产出规则输入？ | 说明 |
| --- | --- | --- |
| 期限 | ✅ 可达 | `task_due_at/goal_deadline/project_deadline` 已在谓词表，facts 正常落库 |
| 保修/订阅到期 | ✅ 可达 | `warranty_until` 或 `warranty_period→valid_until`、`subscription_renewal_at` 已支持 |
| 预算 | ⚠️ 本次已补齐链路 | 原 `budget_limit` **不在受控词表**，规则永远拿不到输入；已新增谓词到 `docs/extraction-schema.md`、Prompt、Parser、mock 抽取器（**待 A review**；A 侧 `predicate` 为 VARCHAR，无需 DDL） |
| 日程冲突 | ⛔ 不可达（依赖任务 5） | `world_events` 在上传链路无数据来源；A 接入 events 入库后方可触发 |

> 结论：单测通过 ≠ 真实链路通过。日程冲突是当前唯一"规则正确但生产不可达"的风险类，
> 已通过 `EVENTS-INGESTION-CONTRACT.md` 给出闭环方案；预算类已在本分支补齐谓词链路。

---

## 6. 任务 4：演示资料（`nexus-ai/testdata/demo/`）

基准日 **2026-11-23**，由脚本按相对偏移生成，更换基准日可重新生成：

```
cd nexus-ai/testdata
python generate_demo_assets.py demo 2026-11-23
java GenerateDemoSchedule.java demo 2026-11-23
```

| 资料 | 类型 | 关键内容 | 预期触发 |
| --- | --- | --- | --- |
| `budget-subscription.txt` | TXT | 月度预算 ¥50；两份订阅各 ¥19 / ¥39，续费 +2 / +12 天 | `BUDGET_OVERSPENT`（high）、`SUBSCRIPTION_EXPIRY_7D`、`SUBSCRIPTION_EXPIRY_30D` |
| `schedule-plan.png` | 图片 | 同日 10:00 / 10:30 两项；次日 09:00 / 15:00 两项；任务 +1 天 | `SCHEDULE_CONFLICT_HOUR`、`SCHEDULE_CONFLICT_DAY`、`DEADLINE_1D` |
| `warranty-invoice.pdf` | PDF | 购买日 -22 天，Warranty Until +8 天 | `WARRANTY_EXPIRY_30D`（low） |

验证：TXT 已通过 mock 抽取（`facts=5`，含 `budget_limit`）；图片中文渲染已目检；
PDF 为 ASCII 内容，供 real 模式 PDF 转图 + OCR 验证。

---

## 7. 任务 5：events 消费方案

见 `nexus-ai/EVENTS-INGESTION-CONTRACT.md`，要点：

- `ExtractionResult` 增加 `events` / `tasks`（B Parser 需同步实现）；
- `event_type` 10→6 显式映射（`deadline` 按关联实体细化）；
- `occurred_at` 取值优先级 `start_at > due_at > end_at`，+08:00 业务日转 UTC；
- `entity_id` 必须尽量解析，否则 A 的风险管线会丢弃该冲突风险；
- `tasks.source_type` 建议扩展 `extracted`（需 DDL），`due_at` 按 +08:00 取日期；
- 应用层幂等键：事件 `(user_id, source_id, event_type, occurred_at, title)`。

**待 A 拍板 7 项**，见该文档第 7 节。

---

## 8. 遗留与下一步

1. ⏳ 凭据到位 → 执行第 3.4 节 real 全链路，回填第 4 节；
2. ⏳ A review：`budget_limit` 谓词（v0.2 Schema）与 events/tasks 契约；
3. ⏳ A 接入 events 入库后，补"上传 `schedule-plan.png` → `SCHEDULE_CONFLICT_*`"端到端验收；
4. ⏳ Schema 顶层字段/命名风格二选一修订（第 3.3 节建议 1）。
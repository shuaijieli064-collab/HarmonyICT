# nexus-ai（成员 B）

AI 与智能引擎交付目录：测试资料集、抽取结果样例、联调说明。
后端实现位于 `nexus-backend/src/main/java/com/nexus/ai/`（B 独占包）。

## 目录

| 路径 | 说明 |
| --- | --- |
| `testdata/invoice.pdf` | 发票样例（PDF，真实验收用 OCR/PDF 转图链路） |
| `testdata/subscription.txt` | 订阅确认样例（TXT，可直接抽取） |
| `testdata/study-plan.png` | 学习计划样例（图片，真实验收用 OCR） |
| `testdata/demo/` | **演示资料三份**（TXT/图片/PDF，覆盖期限·冲突·到期·预算四类风险，基准日 2026-11-23） |
| `testdata/generate_demo_assets.py` | 生成演示 TXT + PDF（可指定基准日，日期按相对偏移计算） |
| `testdata/GenerateDemoSchedule.java` | 生成演示日程图片（JDK 单文件运行） |
| `testdata/generate_invoice_pdf.py` | 发票 PDF 生成脚本（Python 标准库） |
| `testdata/GenerateStudyPlan.java` | 学习计划图片生成脚本（JDK 单文件运行） |
| `testdata/extraction-result.json` | Demo 运行产物（不入库，可删除） |
| `schema_check.py` | 抽取结果 Schema 合规校验（对照 `docs/extraction-schema.md` 第 3/4/9 节） |
| `EVENTS-INGESTION-CONTRACT.md` | events / tasks 入库契约（待 A review，闭环日程冲突风险） |
| `ACCEPTANCE-RECORD.md` | 验收记录与答辩证据索引 |
| `P1-JOINT-DECISION.md` | P1 四项联调拍板数据（含 confidence/factStatus 落库 P0） |
| `ORCHESTRATION-CONTRACT.md` | 抽取入库编排契约（OCR→抽取→落库链路 + PDF 转图说明） |

## 演示资料（覆盖 4 类风险）

`testdata/demo/` 下三份资料按基准日 **2026-11-23** 生成，日期为相对偏移（任务 +1 天、订阅 +2/+12 天、保修 +8 天），
更换基准日可整体重生成，避免演示前日期过期：

```
cd nexus-ai/testdata
python generate_demo_assets.py demo 2026-11-23
java GenerateDemoSchedule.java demo 2026-11-23
```

| 文件 | 类型 | 预期风险 |
| --- | --- | --- |
| `budget-subscription.txt` | TXT | 预算超支 `BUDGET_OVERSPENT`、订阅到期 `SUBSCRIPTION_EXPIRY_*` |
| `schedule-plan.png` | 图片 | 日程冲突 `SCHEDULE_CONFLICT_HOUR/DAY`、期限 `DEADLINE_1D` |
| `warranty-invoice.pdf` | PDF | 保修到期 `WARRANTY_EXPIRY_30D` |

> 日程冲突依赖 events 入库（见 `EVENTS-INGESTION-CONTRACT.md`）；预算依赖新增谓词 `budget_limit`
> （`docs/extraction-schema.md` v0.2，待 A review）。

## Schema 合规校验

```
python schema_check.py <抽取结果 JSON> --source-id <A 上传接口返回的雪花ID>
```

退出码 0 通过 / 1 有阻断级违规。脚本兼容 snake_case 与 camelCase，会单独提示命名风格差异。
当前已知缺口（见 `ACCEPTANCE-RECORD.md` 第 3.3 节）：Prompt 与 mock 输出缺少
`extraction_id / source / language / extracted_at` 四个顶层字段，且命名风格为 camelCase，
与 `docs/extraction-schema.md` 第 9 节 JSON Schema 不一致，待与 A 对齐后统一。

## 运行抽取 Demo

工作目录：`nexus-backend`。

### 本地链路验证（无云凭证）

```
mvn.cmd spring-boot:run -Dspring-boot.run.arguments="--nexus.ai.mode=mock --nexus.ai.demo.enabled=true --nexus.ai.demo.input=../nexus-ai/testdata/subscription.txt"
```

mock 模式用确定性正则抽取，仅验证「原文 → Schema JSON → DTO」链路，**不能作为华为云能力证明**。
mock OCR 只解码纯文本 base64，不识别图片内容。

### 真实华为云调用

先设置环境变量（凭据只走环境变量，禁止写入仓库）：

```
set NEXUS_AI_MODE=real
set NEXUS_AI_MAAS_BASE_URL=https://maas-api.<region>.myhuaweicloud.com
set NEXUS_AI_MAAS_API_KEY=<MaaS API Key>
set NEXUS_AI_MAAS_MODEL=<模型名>
set NEXUS_AI_OCR_IAM_ENDPOINT=https://iam.<region>.myhuaweicloud.com
set NEXUS_AI_OCR_ENDPOINT=https://ocr.<region>.myhuaweicloud.com
set NEXUS_AI_OCR_USERNAME=<IAM 用户名>
set NEXUS_AI_OCR_PASSWORD=<IAM 密码>
set NEXUS_AI_OCR_DOMAIN_NAME=<账号名>
set NEXUS_AI_OCR_PROJECT_ID=<项目 ID>
```

然后运行：

```
mvn.cmd spring-boot:run -Dspring-boot.run.arguments="--nexus.ai.demo.enabled=true --nexus.ai.demo.input=../nexus-ai/testdata/subscription.txt"
```

图片样例把 input 换成 `../nexus-ai/testdata/study-plan.png`；PDF 样例把 input 换成 `../nexus-ai/testdata/invoice.pdf`（需 pom 已引入 PDFBox，见"PDF 转图"说明）。

## 配置项（nexus.ai.*）

| 配置 | 环境变量 | 说明 |
| --- | --- | --- |
| `nexus.ai.mode` | `NEXUS_AI_MODE` | `real`（默认）/ `mock` |
| `nexus.ai.maas.base-url` | `NEXUS_AI_MAAS_BASE_URL` | 只填域名，代码追加 `/v1/chat/completions` |
| `nexus.ai.maas.api-key` | `NEXUS_AI_MAAS_API_KEY` | MaaS API Key |
| `nexus.ai.maas.model` | `NEXUS_AI_MAAS_MODEL` | 模型名 |
| `nexus.ai.maas.temperature` | `NEXUS_AI_MAAS_TEMPERATURE` | 默认 0.1 |
| `nexus.ai.ocr.*` | `NEXUS_AI_OCR_*` | IAM 用户名/密码/账号名/项目 ID |
| `nexus.ai.demo.source-id` | `NEXUS_AI_DEMO_SOURCE_ID` | Demo 输出事实的 `source_id`（默认演示 UUID，真实联调由 A 传入） |

### 真实调用验收脚本（P0，需华为云凭据）

```
cp real-call.env.template real-call.env   # 填入真实凭据（real-call.env 已被 .gitignore 忽略）
bash verify-real-call.sh                   # 依次验收 MaaS 文本抽取 + OCR 图片 + PDF 转图识别
```

脚本会校验 6 项必填凭据，缺一即退出，不会用空凭据空跑；输出文件默认为 `testdata/extraction-result.json`，
可通过 `NEXUS_AI_DEMO_OUTPUT` 覆盖（本机 Maven fork 的 JVM 对 `nexus-ai` 目录无写权限时使用）。
OCR 支持图片（`study-plan.png`）与 PDF（`invoice.pdf`，逐页转图后识别，需 pom 引入 PDFBox）。
凭据只走本地 `real-call.env` 或系统环境变量，**禁止写入仓库**。

### PDF 转图（阶段二遗留，已补齐）

`com.nexus.ai.ocr.PdfPageRenderer` 通过反射调用 Apache PDFBox 逐页渲染 PDF（200 DPI → PNG），
`HuaweiOcrProvider` 遇到 `mimeType=pdf` 时逐页识别并保留真实页码。

- **需 A 在 `nexus-backend/pom.xml` 添加依赖**（pom 属 A 独占，B 未擅自修改）：
  ```xml
  <dependency>
      <groupId>org.apache.pdfbox</groupId>
      <artifactId>pdfbox</artifactId>
      <version>2.0.31</version>
  </dependency>
  ```
- 未加依赖时：PDF 调用抛"需要 Apache PDFBox 依赖"的业务异常，**不影响编译与其它测试**（反射解耦）。
- 加依赖后：PDF 转图自动生效，B 侧代码零改动。详见 `ORCHESTRATION-CONTRACT.md` 第 4 节。

## 风险规则引擎（阶段三）

实现：`com.nexus.ai.risk.DefaultRiskAnalysisProvider` + `risk/rules/` 下 4 条规则。纯计算，不访问数据库，入库由 A 的服务负责。

| 规则 | 输入事实 / 事件 | 阈值 | level | ruleCode | riskType |
| --- | --- | --- | --- | --- | --- |
| 期限监控 | `task_due_at` / `goal_deadline` / `project_deadline` | ≤30 / ≤7 / ≤1 天 | low / medium / high | `DEADLINE_30D/7D/1D` | `contract_deadline` |
| 保修/订阅到期 | `warranty_until` / `subscription_renewal_at` / `subscription_end_at` | 同上 | 同上 | `WARRANTY_EXPIRY_*` / `SUBSCRIPTION_EXPIRY_*` | `cert_expiry` / `contract_deadline` |
| 日程冲突 | `events.occurredAt` | 同日 ≥2 项；同小时 → medium | low / medium | `SCHEDULE_CONFLICT_DAY/HOUR` | `other` |
| 预算约束 | `budget_limit` + `subscription_price`（按 `subscription_cycle` 折算月度） | ≥80% medium，≥100% high | medium / high | `BUDGET_WARNING/OVERSPENT` | `other` |

每条风险输出 `ruleCode`、等级、触发说明和证据事实 ID，模型不参与结论。

> `riskType` 受 A 的 DDL 枚举限制（`cert_expiry/contract_deadline/asset_concentration/data_staleness/other`）。日程冲突与预算约束暂无专属枚举，暂用 `other`；建议 A 在阶段二扩展枚举。

## Agent 工具编排（阶段四）

实现：`com.nexus.ai.agent.DefaultAgentOrchestratorProvider` + `AgentToolRegistry` + `AgentIntentParser`。
只产出候选计划，不落库、不执行；`agent_action_logs` 持久化与 L2 授权由 A 的编排服务负责。

工具白名单（未注册的 `actionType` 不会出现在计划中）：

| actionType | level | targetType | 说明 |
| --- | --- | --- | --- |
| `read_fact` | L0 | fact | 只读当前有效事实 |
| `read_event` | L0 | event | 只读事件时间线 |
| `create_task` | L1 | task | 创建待办，执行前需向用户展示 |
| `update_fact` | L2 | fact | 更新世界模型事实，须用户授权后执行 |

意图规则（规则驱动，可复现）：

- 「查看 / 汇总 / 整理 / 有哪些」→ 只读计划（L0）
- 「待办 / 任务 / 提醒 / 安排」→ `create_task`（L1），自动关联最近到期的保修/订阅/任务事实
- 「更新 / 修改 / 改到」→ `update_fact`（L2），需同时定位到唯一事实与新值；否则降级为只读计划并提示补充信息
- 单次计划最多 10 个操作；空意图不生成操作

## 契约

抽取输出以 `docs/extraction-schema.md` 为准；与 A 的 DTO 映射说明见该文档第 10、12 节。
# 提案：抽取 events 字段落库（已落地）

> 提案人：A　日期：2026-11-23　状态：**已拍板并落地（PR #12 合入 develop）**

## 背景

- B 的抽取输出（extraction-schema）含 `events` 数组（如 `purchase`、`subscription_renewal`），mock 与 real 链路均产出
- A 的 `ExtractionPipelineService.runPipeline`（阶段四编排）当前只消费 `facts` 与 `entities`，**events 未入库**
- 现状后果：`/api/v1/events` 恒为空；C 的事件时间线组件（Dashboard event-timeline）在真实链路下无数据

## 方案对比

### 方案 A：管线消费 events，落 world_events（推荐）

- 映射：`eventId → 幂等键（sourceId+eventId）`、`entityId → 落库后的实体 id`、`occurredAt/validUntil`（schema 中为事件时间）→ `occurred_at`
- 工作量：约 1 个服务方法 + 映射逻辑（参照 persistFact），DDL 无变更（world_events 表已存在）
- 风险：`occurred_at` 时区解析（schema 输出 `+08:00` 偏移，需归一 UTC）；`entityId` 需在实体落库后回填映射（`ent_1` → 数据库 id）

### 方案 B：Demo 不展示事件时间线，维持现状

- 零工作量，但 C 的 Dashboard 时间线只能继续走 Mock
- 与「唯一核心闭环：前端可视化含时间线」的立项要求有偏差

## 建议

**方案 A**，排在 B 的真实链路验收（MaaS/OCR）之后实施，理由：

1. world_events 表与 `/api/v1/events` 契约已就绪，纯后端增量，不动 B/C 代码
2. 事件时间线是 Demo 主线页面组件，真实数据比 Mock 更支撑答辩
3. 不阻塞当前联调——C 前端联调可先用 facts/risks 数据

## 决策记录

| 日期 | 决策 | 参与方 |
| --- | --- | --- |
| 2026-11-23 | 采用方案 A，events 入库与抽取契约 snake_case 统一随 PR #12（feature/backend-a-events）实施并合入 develop，时间线组件真实数据链路打通 | A（实施）、B/C（契约复核） |
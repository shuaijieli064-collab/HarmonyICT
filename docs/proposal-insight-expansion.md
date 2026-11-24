# 提案：洞察实验室（决策推演 + 引擎族 + 未来时间线）

> 提案人：A　日期：2026-11-23　状态：**已拍板执行**（用户拍板 P1/P2/P3 三项全做，A 代 B/C 实施）
> 关联：`docs/competition-roadmap.md` 阶段一扩充项

## 背景

前端 `decision-lab`（决策实验室）与 `asset-center`（资产中心）目前为占位页。用户拍板把立项时收敛掉的「决策模拟」以轻量形态落地，同时给规则引擎加「引擎族」叙事与「未来 30 天」时间线投影，增强演示纵深。

## 方案

### P1 决策推演（decision-lab 核心）

新增只读推演 API：`POST /api/v1/decision/simulate`。基于实体已有事实（subscription_price / subscription_cycle / auto_renew / budget_limit）做**沙盘投影**：

- `scenario=cancel`：未来 12 个月节省曲线 + 该实体名下未决风险视为消除
- `scenario=keep`：未来 12 个月支出曲线 + 假设性风险（续费 30 天内临近 / 月费超预算上限）

**红线声明**：推演结果不落库、不产生真实风险记录；假设性风险仅以「SIM_KEEP_*」ruleCode 标记为推演口径，与规则引擎同源阈值（1D/7D/30D 分级）。真实风险仍只由 RiskRuleEngine 产生。

### P2 引擎族分层

不改动 B 的 `ai/risk/rules/` 实现，在 A 侧新增 `ruleCode → 引擎族` 映射层：

| 引擎族 | ruleCode 前缀 |
| --- | --- |
| FINANCE 财务 | `BUDGET_*` |
| SUBSCRIPTION 订阅 | `SUBSCRIPTION_*` |
| DEVICE 设备 | `WARRANTY_*`、`CERT_*` |
| LEARNING 学习 | `DEADLINE_*`、`SCHEDULE_*` |
| DATA_QUALITY 数据质量 | `DATA_*` |

### P3 未来时间线

`GET /api/v1/insight/timeline?days=30`：投影 = active 事实的 `valid_until`（到期/截止）+ 事件 `occurred_at`，按日期升序，level 沿用规则引擎同源口径（≤1d high、≤7d medium、其余 low）。

## API 契约（供前端 C 接线）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/insight/overview` | 引擎族风险分布统计 |
| GET | `/api/v1/insight/timeline?days=30` | 未来 N 天时间线（1–90 夹取） |
| POST | `/api/v1/decision/simulate` | 假设推演，body `{entityId, scenario: cancel\|keep}` |

错误码复用：`20101 WORLD_ENTITY_NOT_FOUND`、`90001 PARAM_INVALID`。无 DDL 变更、无新错误码。

## 分工说明

本提案由 A 代 B/C 实施（用户拍板）：后端 insight 模块（A 域）+ 前端 asset-center / decision-lab 接线（代 C）。不触碰 `ai/risk/rules/`（B 域）与 PR #16 已改动文件（world-model、dashboard 组件、constants、types）。

## 决策记录

| 日期 | 决策 | 参与方 |
| --- | --- | --- |
| 2026-11-23 | P1/P2/P3 三项全做，A 代 B/C 实施，单 PR 交付 | 用户 |

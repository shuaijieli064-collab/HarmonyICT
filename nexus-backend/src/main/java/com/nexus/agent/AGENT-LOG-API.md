# Agent 执行日志接口说明（v3/agent · V3 增补）

> 适用对象：前端交付线（Agent Center 执行时间线）。
> 本文件是 `com.nexus.agent` 包内的增补说明，不改 `docs/api-specification.md` 主契约，也不改数据库表结构。
> 基线：`v3/agent` 提交 `7347b7c`；与主契约冲突时以主契约为准，本文件只补充 V3 新增字段与接口。

---

## 1. 分页查询执行日志

```http
GET /api/v1/agent/runs/{id}/logs?level=L2&result=pending&page=1&pageSize=20
Authorization: Bearer <token>
```

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | path | 是 | `agent_runs.id`，BIGINT 雪花 id 按字符串处理 |
| `level` | query | 否 | `L0` / `L1` / `L2` |
| `result` | query | 否 | `pending` / `success` / `failure` / `denied` |
| `page` | query | 否 | 从 1 开始，默认 1 |
| `pageSize` | query | 否 | 1–100，默认 20 |

响应包裹统一为 `Result<PageResult<AgentActionLogResponse>>`：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "records": [
      {
        "id": "1948...",
        "actionType": "update_fact",
        "level": "L2",
        "targetType": "fact",
        "targetId": "1948...",
        "payload": "{\"factId\":\"1948...\",\"predicate\":\"warranty_until\",\"currentValue\":\"2026-12-01\",\"objectValue\":\"2027-01-01\",\"audit\":{...}}",
        "result": "pending",
        "authorized": false,
        "createdAt": "2026-11-24T01:02:03Z",
        "audit": {
          "action": "update_fact",
          "authorizedBy": null,
          "authorizedAt": null,
          "decision": null,
          "changedField": "warranty_until",
          "beforeValue": "2026-12-01",
          "afterValue": "2027-01-01",
          "failureReason": null,
          "executionSummary": null
        }
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  },
  "timestamp": "2026-11-24T01:02:03Z"
}
```

**数据隔离**：`user_id` 只从 JWT 解析；查询强制带 `user_id + run_id`。他人 run 返回 `60101`。
**兼容性**：`AgentActionLogResponse` 原有 9 个字段顺序与类型不变，`audit` 为第 10 个附加字段；旧前端可忽略。

### 与 run 详情的关系

`GET /api/v1/agent/runs/{id}` 返回的 `data.logs` 与上面 `records[]` 结构完全一致（同样含 `audit`）。
时间线可二选一：一次拿全量（详情），或按状态分页加载（本接口）。主契约第 8 节字段定义继续有效。

---

## 2. `AgentAuditTrail` 字段

| 字段 | 类型 | 含义 | 典型取值 |
| --- | --- | --- | --- |
| `action` | string | 动作类型 | `read_risk` / `create_task` / `update_fact` |
| `authorizedBy` | string | 授权人 userId（JWT 上下文，非前端传参） | `"1948..."` |
| `authorizedAt` | string | 授权时间，ISO 8601 UTC | `2026-11-24T01:02:03Z` |
| `decision` | string | 授权结论 | `approved` / `denied`；L0 为 `null` |
| `changedField` | string | 变更的世界模型字段名 | `warranty_until` |
| `beforeValue` | string | 变更前值 | `2026-12-01` |
| `afterValue` | string | 变更后值 | `2027-01-01` |
| `failureReason` | string | 执行失败原因 | `payload 缺少 factId` |
| `executionSummary` | string | 只读工具执行摘要 | `read_risk 返回 2 条` |

**注意**：BIGINT 字段（`id`、`targetId`、`authorizedBy` 语义上的用户 id）一律按字符串渲染，避免 JS 精度丢失。

---

## 3. 存储结构（前端只需知道，不需直接消费）

审计信息写入 `agent_action_logs.payload` 的顶层 `audit` 对象，响应时解析成 `audit` 结构化字段：

```json
{
  "factId": "1948...",
  "predicate": "warranty_until",
  "currentValue": "2026-12-01",
  "objectValue": "2027-01-01",
  "audit": {
    "action": "update_fact",
    "authorizedBy": "1948...",
    "authorizedAt": "2026-11-24T01:02:03Z",
    "decision": "approved",
    "changedField": "warranty_until",
    "beforeValue": "2026-12-01",
    "afterValue": "2027-01-01",
    "executedAt": "2026-11-24T01:02:04Z",
    "executionResult": "success"
  }
}
```

这样做的原因：`agent_action_logs` 表结构与主契约已定稿，V3 不跨线改 DDL / docs。
`payload` 仍是 TEXT 存 JSON 字符串，前端如需展示原始载荷仍按 `JSON.parse(payload)` 处理。

---

## 4. 只读工具 `toolResult`（时间线过程展示）

V3 新增四类 L0 工具，执行结果写入 `payload.toolResult`（同时生成 `audit.executionSummary`）：

| actionType | `toolResult` 主要字段 | 说明 |
| --- | --- | --- |
| `read_risk` | `count`、`items[]`（`id`/`riskType`/`level`/`status`/`ruleCode`/`title`） | 规则引擎已产生的风险 |
| `read_task` | `count`、`items[]`（`id`/`title`/`status`/`dueDate`） | 当前用户待办 |
| `explain_risk` | `riskId`、`ruleCode`、`explanation`、`source`、`evidenceFactIds[]` | 只解释已有风险，不产生新结论 |
| `suggest_action` | `count`、`suggestions[]`、`writesPerformed=false` | 建议文案，绝不执行写操作 |

`read_fact` / `read_event` 为既有 L0 工具，沿用原 payload，不携带 `toolResult`。

---

## 5. 错误码

| code | 场景 |
| --- | --- |
| `60101` | run 不存在或不属于当前用户（含日志接口越权访问） |
| `60102` | 授权日志不属于当前用户 / 动作未授权 |
| `90001` | `page < 1`、`pageSize` 越界、日志 id 非法、非 L2 日志请求授权 |

---

## 6. 前端接线检查清单

- [ ] 时间线状态判定沿用主契约：`level === "L2" && result === "pending" && authorized === false` 为待授权。
- [ ] 授权成功后刷新 run 详情与本接口，`audit.decision` 应变为 `approved`，`executionResult` 为 `success` 或 `failure`。
- [ ] 失败态展示 `audit.failureReason`，不要自行编造原因。
- [ ] 所有 `*Id` 按字符串处理；时间按 ISO 8601 解析。
- [ ] 不要传 `userId` 参数；服务端从 JWT 取，传了会被守卫拒绝。

---

*维护人：Agent 能力扩展线（B） · 更新：2026-11-24*

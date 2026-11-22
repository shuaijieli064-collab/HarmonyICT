# NEXUS · 统一 API 契约（成员 A 主笔，成员 C Review）

> 状态：阶段一定稿候选。**本文档是成员 C 前端 Mock 开发的唯一依据**，字段必须按此实现。
> 变更须经 PR 合并到 develop 后再改代码（docs/00 整合约定）。

---

## 1. 通用约定

| 项 | 约定 |
| --- | --- |
| 基础前缀 | `/api/v1` |
| 认证方式 | `Authorization: Bearer <token>`；除 `POST /auth/register`、`POST /auth/login`、健康检查外全部必需 |
| 响应包裹 | 所有接口返回 `Result<T>`：`{ "code": 0, "message": "成功", "data": ..., "timestamp": "2026-11-21T08:00:00Z" }` |
| 成功码 | `code = 0`；非 0 一律视为失败（HTTP 层业务错误也返回 200，仅系统级错误返回 5xx） |
| 时间格式 | ISO 8601 UTC，如 `2026-11-21T08:00:00Z` |
| 分页请求 | `page`（从 1 开始）、`pageSize`（默认 20，最大 100） |
| 分页响应 | `data: { "records": [...], "total": 123, "page": 1, "pageSize": 20 }` |
| 字段命名 | 小驼峰；禁止前端传 `userId` 参数 —— 服务端一律从 JWT 上下文解析 |
| BIGINT 字段 | 所有 `id`/`userId`/`*Id` 字段服务端序列化为**字符串**（雪花 ID 超出 JS Number 安全整数范围，直接用数字会精度丢失），前端按 string 处理 |

### 错误码总表（与后端 `ResultCode.java` 同步维护）

| code | 含义 | 模块 |
| --- | --- | --- |
| 0 | 成功 | - |
| 10101 | 用户名或密码错误 | 认证 |
| 10102 | 用户名已存在 | 认证 |
| 10103 | 令牌无效或已过期 | 认证 |
| 10104 | 令牌已过期 | 认证 |
| 10105 | 未登录或令牌缺失 | 认证 |
| 20101 | 实体不存在 | 世界模型 |
| 20102 | 事实不存在 | 世界模型 |
| 20103 | 事实状态冲突 | 世界模型 |
| 30101 | 上传文件为空 | 文件 |
| 30102 | 不支持的文件类型 | 文件 |
| 30103 | 文件超过大小限制 | 文件 |
| 30104 | 文件存储失败 | 文件 |
| 40101 | 风险记录不存在 | 风险 |
| 50101 | 任务不存在 | 任务 |
| 50102 | 任务状态流转不合法 | 任务 |
| 60101 | Agent 执行记录不存在 | Agent |
| 60102 | Agent 操作未被授权 | Agent |
| 90011 | 抽取请求缺少有效的来源 sourceId | 抽取校验 |
| 90012 | 抽取结果不符合约定 Schema | 抽取校验 |
| 90013 | 抽取谓词不在登记清单内 | 抽取校验 |
| 90001 | 请求参数不合法 | 通用 |
| 90002 | 资源不存在 | 通用 |
| 90000 | 服务器内部错误 | 通用 |

> 说明：阶段一已实现认证与健康检查接口；其余接口为 C 端 Mock 与 B/A 后续实现的契约基准。

## 2. 认证模块（阶段一已实现）

### 2.1 注册 `POST /api/v1/auth/register`

请求：
```json
{ "username": "nexus_user", "password": "passw0rd!", "nickname": "小明", "email": "user@example.com" }
```
校验：username 3-32 字符；password 8-64 字符；nickname/email 可选。

响应 `data`：
```json
{ "token": "eyJhbGciOi...", "userId": "1948xxxxxxxxxxxx", "username": "nexus_user", "nickname": "小明" }
```

### 2.2 登录 `POST /api/v1/auth/login`

请求：`{ "username": "...", "password": "..." }`
响应：同 2.1。失败：`code=10101`。

### 2.3 退出 `POST /api/v1/auth/logout`（需认证）

请求头：`Authorization: Bearer <token>`。响应：`data: null, code: 0`。服务端将 token 加入黑名单（Redis 可用时）。

## 3. 用户模块

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/users/me` | 获取当前用户资料：`{ "userId", "username", "nickname", "email", "createdAt" }` |
| `PUT /api/v1/users/me` | 更新昵称/邮箱，请求 `{ "nickname", "email" }` |

## 4. 文件上传（阶段二实现，契约先行）

### 4.1 上传资料 `POST /api/v1/sources/upload`

`multipart/form-data`：`file`（必填）。
限制：单文件 ≤ 20MB；类型限 `pdf / docx / xlsx / txt / md / png / jpg`。

响应 `data`：
```json
{ "sourceId": "1948...", "fileName": "资产证明.pdf", "mimeType": "application/pdf", "fileSize": 1048576, "status": "pending" }
```

### 4.2 抽取结果获取方式（2026-11-22 确认，异步两步，契约先定）

上传接口**只负责落库并返回 `sourceId`**，抽取由 B 的 AI 引擎后台异步完成，**不新增独立抽取结果接口**。前端获取流程：

1. `POST /api/v1/sources/upload` → 拿到 `sourceId`（`status=pending`）。
2. 轮询 `GET /api/v1/facts?sourceId=<sourceId>&status=active`（见 5.2）：有记录即抽取完成；为空则继续轮询（建议间隔 2s，超时 60s 后提示失败并检查来源详情 `status=failed`）。

约定：`sourceId` 是后端上传接口分配的雪花数字字符串，B 侧抽取流程只做透传，**禁止自造 UUID**（PR #1 review 阻塞项 1 拍板结论）。

### 4.3 来源列表 `GET /api/v1/sources`

Query：`page`、`pageSize`、`status`（可选：pending/parsing/parsed/failed）。
响应 `data.records[]`：同 4.1 元素 + `createdAt`。

### 4.4 来源详情 `GET /api/v1/sources/{sourceId}`

## 5. 世界模型 CRUD

### 5.1 实体

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/entities` | 创建：`{ "entityType": "asset", "name": "XX基金", "attributes": "{\"amount\":50000}" }`（attributes 为 **JSON 字符串**，见下方说明） |
| `GET /api/v1/entities` | 分页：`page/pageSize/entityType(可选)/keyword(可选，按 name 模糊)` |
| `GET /api/v1/entities/{id}` | 详情 |
| `PUT /api/v1/entities/{id}` | 更新 name/attributes |
| `DELETE /api/v1/entities/{id}` | 逻辑删除 |

响应 `data`（实体）：
```json
{ "id": "1948...", "entityType": "asset", "name": "XX基金", "attributes": "{\"amount\":50000}", "createdAt": "...", "updatedAt": "..." }
```
`entityType` 取值：`person / asset / event / document / org`。
**`attributes` 类型说明（2026-11-22 与前端对齐）**：实体扩展属性以 **JSON 字符串**（非对象）传输与存储（TEXT 列，见 database-design.md 设计原则 6——H2/PG 双端 DDL 统一）。前端取值需 `JSON.parse(attributes)`，提交需 `JSON.stringify(...)`；`attributes` 为 `{}` 或非对象 JSON（如数组）也合法。

### 5.2 事实

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/facts` | 创建：`{ "subjectId", "predicate": "monthly_income", "objectValue": "12000", "sourceId"(可选), "validFrom"(可选), "validUntil"(可选，证书类事实到期时间，CERT_EXPIRY_30D 规则依赖), "valueType"(可选), "unit"(可选), "normalizedValue"(可选), "evidence"(可选) }` |
| `GET /api/v1/facts` | 分页：`page/pageSize/subjectId(可选)/predicate(可选)/sourceId(可选)/status(默认 active)`；`sourceId` 为抽取结果轮询入口（见 4.2） |
| `GET /api/v1/facts/{id}` | 详情 |
| `PUT /api/v1/facts/{id}` | 更新 objectValue：旧事实置 superseded，新事实落库（链式失效） |
| `DELETE /api/v1/facts/{id}` | 撤回：status 置 retracted |

响应 `data`（事实）：
```json
{ "id": "1948...", "subjectId": "1948...", "predicate": "monthly_income", "objectValue": "12000",
  "valueType": "number", "unit": "元", "normalizedValue": "12000", "evidence": "每月工资流水 12000 元",
  "sourceId": "1948...", "confidence": 0.92, "validFrom": "...", "validUntil": null, "status": "active",
  "factStatus": "confirmed", "createdAt": "...", "updatedAt": "..." }
```
`confidence` 0-1；`status` 取值：`active / superseded / retracted`；`factStatus` 取值以 B 主笔的 `extraction-schema.md` 为准（暂定 `confirmed / inferred / unverified`）；`valueType` 取值：`string / number / date / boolean`。

### 5.3 事件

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/events` | `{ "entityId"(可选), "eventType": "career", "title": "入职A公司", "description", "occurredAt", "sourceId"(可选) }` |
| `GET /api/v1/events` | 分页：`page/pageSize/eventType(可选)/entityId(可选)`，按 occurredAt 倒序 |

`eventType` 取值：`education / career / asset / health / contract / other`。

## 6. 风险列表（阶段三实现）

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/risks` | 分页：`page/pageSize/level(可选)/status(默认 open)/riskType(可选)` |
| `GET /api/v1/risks/{id}` | 详情（含相关事实快照） |
| `PUT /api/v1/risks/{id}/acknowledge` | 确认风险：status → acknowledged |
| `PUT /api/v1/risks/{id}/resolve` | 解决风险：status → resolved |

响应 `data`（风险）：
```json
{ "id": "1948...", "riskType": "cert_expiry", "level": "high", "title": "PMP证书 30 天内到期",
  "description": "证书于 2027-01-20 到期", "entityId": "1948...", "ruleCode": "CERT_EXPIRY_30D",
  "status": "open", "createdAt": "...", "updatedAt": "..." }
```
`level`：`low / medium / high`（2026-11-22 由 mid 统一为 medium，前端按 medium 实现无需改动）；`status`：`open / acknowledged / resolved`。

## 7. 任务模块（阶段三实现）

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/tasks` | `{ "title", "description"(可选), "priority": 0, "dueDate"(可选), "sourceType"(默认 manual), "riskId"(可选) }` |
| `GET /api/v1/tasks` | 分页：`page/pageSize/status(可选)/sourceType(可选)`，优先按 dueDate 升序 |
| `GET /api/v1/tasks/{id}` | 详情 |
| `PUT /api/v1/tasks/{id}` | 更新 title/description/priority/dueDate |
| `PUT /api/v1/tasks/{id}/status` | 状态流转：`{ "status": "done" }`；合法流转见下表 |
| `DELETE /api/v1/tasks/{id}` | 删除 |

`status` 合法流转：`todo → in_progress → done`；`todo/in_progress → cancelled`；非法流转返回 `50102`。
`priority`：0 普通 / 1 重要 / 2 紧急；`sourceType`：`manual / risk / agent`。

## 8. Agent 执行记录（阶段三实现）

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/agent/runs` | 发起执行：`{ "intent": "整理本月资产变动", "triggerType": "manual" }` |
| `GET /api/v1/agent/runs` | 分页：`page/pageSize/status(可选)` |
| `GET /api/v1/agent/runs/{id}` | 详情：含 summary、操作日志列表 |
| `POST /api/v1/agent/runs/{id}/execute` | 触发执行编排（异步），立即返回 run 当前快照 |
| `POST /api/v1/agent/runs/{id}/authorize` | L2 操作授权：`{ "logIds": ["1948..."], "approve": true }` |

响应 `data`（run）：
```json
{ "id": "1948...", "triggerType": "manual", "intent": "整理本月资产变动", "status": "running",
  "summary": null, "startedAt": "...", "finishedAt": null }
```
`status` 枚举：`running / succeeded / failed / cancelled`（**无 waiting_auth 状态**）。
**"待授权"判定（2026-11-22 与前端对齐）**：由前端从操作日志推导——日志满足 `level === "L2" && result === "pending" && authorized === false` 即视为待授权，展示授权入口；授权通过（`authorize approve=true`）后后端立即真实执行，前端应刷新 run 详情与任务列表。

响应 `data`（操作日志，嵌套于 run 详情）：
```json
{ "id": "1948...", "actionType": "create_task", "level": "L2", "targetType": "task",
  "targetId": null, "payload": "{\"title\":\"更新基金定投比例\"}", "result": "pending",
  "authorized": false, "createdAt": "..." }
```
**`payload` 类型说明（2026-11-22 与前端对齐）**：操作载荷以 **JSON 字符串**（非对象）传输（TEXT 列），前端展示需 `JSON.parse(payload)`。`targetId`：新建类动作为 `null`（如 create_task 执行成功后回填新任务 id）；授权通过后由后端动作执行器回填。

## 9. 附录

### 9.1 健康检查（C 联调探活用）

- `GET /actuator/health`：`{ "status": "UP" }`（免认证）。
- `GET /api/v1/health`：业务级健康检查（免认证，返回服务名与当前认证用户）。

### 9.2 C 端 Mock 开发提示

1. 统一按 `Result<T>` 包裹解析；`code !== 0` 走错误提示分支。
2. Mock 数据须放入独立 mock 文件并标注（规范 3.3），禁止散落在组件里。
3. 时间字段一律按 ISO 8601 解析展示。
4. 契约问题（字段缺失/语义不明）在群里提出，走 PR 修改本文档，不要自行假设字段。
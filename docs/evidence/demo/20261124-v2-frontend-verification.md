# V2 前端验证记录（2026-11-24）

> 执行线：对话 C · 前端与交付物线（`v2/front` 工作树，基线 `2501ccc`）
> 原则：端到端实跑，不盲信 PR 合并记录。

## 1. 构建与规范

| 检查 | 命令 | 结果 |
|---|---|---|
| 生产构建（Mock 默认关闭态） | `npm run build` | ✅ 通过，`BUILD_EXIT=0`，Next.js 16.4.6 / TypeScript 17.4s / 静态页 10/10 |
| 代码规范 | `npm run lint` | ✅ 通过，`LINT_EXIT=0`，零告警 |
| Mock 开启态运行时 | `NEXT_PUBLIC_USE_MOCK_DATA=true npm run dev` + 首页渲染探测 | ✅ 首页 200，「今日行动」区块走 MOCK 分支（无"设 NEXT_PUBLIC_USE_MOCK_DATA=true 开启"占位提示） |

> 环境说明：Mock 开启态的**生产构建**二次执行被沙箱批量删除保护拦截（清理 `.next` 触发 363 文件阈值，非代码错误）。
> 默认关闭态生产构建已通过；开启态以 dev 运行时验证替代，二者共享同一份代码，仅内联的环境变量值不同。

## 2. 路由可达（生产服务 `next start -p 3100`）

| 路由 | 状态码 | 路由 | 状态码 |
|---|---|---|---|
| `/` 驾驶舱 | 200 | `/agent-center` Agent 中心 | 200 |
| `/world-model` 世界模型 | 200 | `/asset-center` 资产中心 | 200 |
| `/intelligence-center` 情报中心 | 200 | `/login` 登录 | 200 |
| `/decision-lab` 决策实验室 | 200 | `/nonexistent-check`（对照） | 404 ✅ |

**路由口径**：构建产物列出 8 个条目 = **7 个业务路由 + 1 个框架内置 `/_not-found`**（404 页，非业务页面）。口径已由主控统一更正，无待确认项。

## 3. #16 五项功能逐项走查

| # | 功能 | 代码载体 | 走查结论 |
|---|---|---|---|
| 1 | 实体关系图谱 | `components/world/entity-graph.tsx` | ✅ ECharts 力导向、实体/事实双节点、动态 import 规避 SSR window；含 loading/error/empty/未登录四态 |
| 2 | Mock 开关 | `lib/constants.ts:73` | ✅ `USE_MOCK_FALLBACK = process.env.NEXT_PUBLIC_USE_MOCK_DATA === "true"`，默认关闭显示占位说明，开启渲染驾驶舱目标进度/今日行动演示数据 |
| 3 | 风险徽章 | `components/dashboard/risk-summary-card.tsx`、`lib/constants.ts:34` | ✅ 高/中/低 → danger/warning/success 语义色，另含规则码徽章；情报中心可展开关联事实快照 |
| 4 | L2 二次确认 | `components/agent/runs-panel.tsx:309-356` | ✅ 点击授权先进 confirming 态，显示「该操作将真实执行并写入任务/事实」，含确认/取消；拒绝走独立分支；确认后刷新 run 详情与任务列表 |
| 5 | 上传兜底 | `services/upload.ts`、`components/upload/upload-panel.tsx` | ✅ 白名单+20MB+空文件预校验、两段进度（XHR 上传 + 2s/60s 轮询）、失败原因展示、复用 sourceId 重试轮询、超时区分 failed/仍在解析 |

## 4. 不变红线静态核对

- 风险结论：前端仅展示后端 `ruleCode` 与关联事实，无前端自造风险判断 ✅
- Agent 边界：仅 L0–L2 授权交互，无支付/外部高风险操作入口 ✅
- 凭据：前端无硬编码密钥，token 仅存 localStorage `nexus_token` ✅

## 5. 边界与待确认

1. ~~「8 路由」口径差异~~ → 已统一为「7 业务路由 + 1 框架内置 `/_not-found`」，无待确认项。
2. Agent 执行日志前端渲染字段需与 Agent 扩展线后端接口对齐（按 `docs/api-specification.md`）。
3. 决策实验室三块（引擎族/时间线/沙盘）为只读展示，明确"不产生真实风险"，答辩须讲清边界。

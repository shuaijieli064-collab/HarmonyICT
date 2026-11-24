# V3 窄屏适配与双主题一致性复查（2026-11-24）

> 执行线：对话 C · 前端与交付物线（`v2/front`）

## 1. 复查方法

静态走查 + dev 运行时编译：
- 逐页检查 7 个业务路由的响应式类分布与布局容器
- 解析 `globals.css` 对比 `:root`（深色）与 `[data-theme="light"]`（浅色）的 token 覆盖
- dev 模式逐路由 curl 触发编译，确认 0 错误

## 2. 已有基础（确认到位，未改动）

| 项 | 结论 |
|---|---|
| 导航切换 | `side-nav` 为 `hidden lg:flex`，`mobile-nav` 为 `lg:hidden`，断点一致 ✅ |
| 主容器 | `min-w-0` 防 flex 溢出 + `max-w-6xl` + `px-4 lg:px-8` ✅ |
| 防主题闪烁 | layout 内联脚本首帧前按 localStorage/系统偏好设 `data-theme` ✅ |
| 表格/表单 | `asset-table`、`world-panel` 已有 `min-w-0` + `overflow` 处理 ✅ |
| 主题切换器 | `theme-toggle` 用 `useSyncExternalStore`，无 hydration 不一致 ✅ |

## 3. 发现并修复的问题

| # | 问题 | 修复 |
|---|---|---|
| 1 | 浅色主题未覆盖 `--success/#34d399`、`--warning/#fbbf24`、`--danger/#f87171`、`--info/#60a5fa`、`--accent` 等语义色，白磨砂背景上对比度偏低 | `globals.css` 的 `[data-theme="light"]` 段补充深色变体：`--accent:#4f46e5`、`--success:#059669`、`--warning:#b45309`、`--danger:#dc2626`、`--info:#2563eb` 等；修复后 light 未覆盖 token 仅剩 `--glass-blur`/`--glass-blur-strong`（纯尺寸，深浅一致合理） |
| 2 | Agent 中心创建表单 `flex gap-2` 在极窄屏挤压输入框 | 改为 `flex flex-col gap-2 sm:flex-row`，输入框加 `min-w-0 flex-1`，按钮 `shrink-0` |
| 3 | 执行记录行 `flex items-center justify-between` 窄屏下状态徽章/按钮换行不佳 | 加 `flex-wrap` |

## 4. 验证

- `npm run lint`：通过（LINT_EXIT=0）
- dev 逐路由编译：7/7 路由 200，日志零错误
- 主题 token 覆盖复核：语义色全部覆盖，无遗漏

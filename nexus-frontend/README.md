# NEXUS 前端（nexus-frontend）

华为 ICT 创新赛赛题 1「基于华为云产品打造 AI 创新应用」参赛作品 **NEXUS · 个人资料智能管家** 的前端工程。
核心链路：上传资料 → AI 抽取结构化事实 → 个人世界模型 → 主动风险发现 → 授权 Agent 生成待办。

技术栈：Next.js（App Router）+ React + TypeScript + Tailwind CSS + Framer Motion + ECharts。
视觉规范：深色科技感驾驶舱，统一见 `docs/design-system.md`。

## 启动

```bash
npm install   # 首次安装依赖
npm run dev   # 开发服务器，默认 http://localhost:3000
npm run build # 生产构建校验
npm run lint  # ESLint 检查
```

## 页面路由（App Router）

| 路由 | 页面 | 阶段一状态 |
| --- | --- | --- |
| `/` | Dashboard · 个人智能驾驶舱（当前状态/目标进度/事件时间线/风险摘要/Agent 任务/今日行动/上传面板） | 已实现（Mock 数据） |
| `/world-model` | World Model · 实体关系图谱 | 骨架，阶段二接 ECharts |
| `/intelligence-center` | Intelligence Center · 风险中心 | 半实现（复用 Mock 风险列表） |
| `/decision-lab` | Decision Lab · 决策实验室 | 骨架，阶段四 |
| `/agent-center` | Agent Center · Agent 执行中心 | 半实现（复用 Mock 任务列表） |
| `/asset-center` | Asset Center · 资产中心 | 骨架，依赖实体数据结构定稿 |

## 目录结构

```
nexus-frontend/
├── app/                # Next.js App Router 页面与全局布局
├── components/
│   ├── dashboard/      # Dashboard 六大模块卡片组件
│   ├── layout/         # side-nav（桌面）/ mobile-nav（移动端）全局导航
│   └── upload/         # 文件上传交互组件（拖拽/选择/进度/结果）
├── lib/                # constants.ts 全站常量、utils.ts 工具函数
├── mock/               # Mock 数据（已标注，联调时替换为 services/ 真实接口）
├── services/           # API 请求封装（upload.ts 当前为 Mock 实现）
└── types/              # 全局 TypeScript 类型（含 Result<T> 契约包裹）
```

## 已创建文件清单（阶段一）

- 工程：`package.json`、`next.config.ts`、`tsconfig.json`、`eslint.config.mjs`
- 页面：`app/layout.tsx`、`app/globals.css`（设计 Token）、`app/page.tsx` 及 5 个子页面骨架
- 布局：`components/layout/side-nav.tsx`、`components/layout/mobile-nav.tsx`
- Dashboard 组件：`status-card`、`goal-progress`、`event-timeline`、`risk-summary-card`、`agent-task-card`、`today-actions`
- 上传：`components/upload/upload-panel.tsx`、`services/upload.ts`
- 基础设施：`lib/constants.ts`、`lib/utils.ts`、`mock/dashboard.ts`、`types/index.ts`
- 文档：`docs/design-system.md`（配色/字体/间距/组件规范/动效/响应式）

## 待联调接口清单（等待 docs/api-specification.md 定稿）

| 接口（暂定） | 用途 | 当前替代 |
| --- | --- | --- |
| `POST /api/v1/ingestion/files` | 文件上传与 AI 抽取 | `services/upload.ts` Mock（模拟进度与抽取结果） |
| Dashboard 指标/目标/事件/风险/Agent 任务/行动查询接口 | 驾驶舱各模块数据 | `mock/dashboard.ts` |

> 依据团队规范：契约未定稿前用 Mock 先行开发，Mock 数据集中在 `mock/` 并标注；联调时只替换 `services/` 层实现，组件层不感知。

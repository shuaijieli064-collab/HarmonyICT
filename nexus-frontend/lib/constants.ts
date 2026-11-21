/* 全站常量集中管理，禁止魔法值散落 */

export interface NavItem {
  href: string;
  label: string;
}

/** 六大页面导航，顺序即产品主流程 */
export const NAV_ITEMS: readonly NavItem[] = [
  { href: "/", label: "驾驶舱" },
  { href: "/world-model", label: "世界模型" },
  { href: "/intelligence-center", label: "情报中心" },
  { href: "/decision-lab", label: "决策实验室" },
  { href: "/agent-center", label: "Agent 中心" },
  { href: "/asset-center", label: "资产中心" },
] as const;

/** ECharts 统一配色顺序（docs/design-system.md 5.5） */
export const CHART_COLORS: readonly string[] = [
  "#22D3EE",
  "#A78BFA",
  "#34D399",
  "#FBBF24",
  "#F87171",
] as const;

/** ECharts 公共样式：坐标轴文字、分割线颜色 */
export const CHART_BASE = {
  axisLabel: { color: "#94A3B8" },
  splitLine: { lineStyle: { color: "rgba(148,163,184,0.15)" } },
} as const;

/** 风险等级 → 展示文案与配色类名（全站统一语义） */
export const RISK_LEVEL_MAP = {
  high: { label: "高风险", text: "text-danger", bg: "bg-danger/15" },
  medium: { label: "需关注", text: "text-warning", bg: "bg-warning/15" },
  low: { label: "正常", text: "text-success", bg: "bg-success/15" },
} as const;

/** Agent 任务状态 → 展示文案与配色 */
export const AGENT_TASK_STATUS_MAP = {
  running: { label: "执行中", text: "text-primary", bg: "bg-primary/15" },
  waiting_auth: { label: "待授权", text: "text-warning", bg: "bg-warning/15" },
  done: { label: "已完成", text: "text-success", bg: "bg-success/15" },
  failed: { label: "失败", text: "text-danger", bg: "bg-danger/15" },
} as const;

/** 允许上传的文件类型与大小上限（契约 docs/api-specification.md 4.1：≤20MB，pdf/docx/xlsx/txt/md/png/jpg） */
export const UPLOAD_ACCEPT = ".pdf,.docx,.xlsx,.txt,.md,.png,.jpg,.jpeg";
export const UPLOAD_MAX_SIZE = 20 * 1024 * 1024;
/** 上传大小上限的展示文案（MB），避免组件层硬编码与常量不同步 */
export const UPLOAD_MAX_SIZE_MB = UPLOAD_MAX_SIZE / (1024 * 1024);
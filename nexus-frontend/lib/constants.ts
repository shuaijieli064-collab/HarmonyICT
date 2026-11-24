/* 全站常量集中管理，禁止魔法值散落 */

export interface NavItem {
  href: string;
  label: string;
}

/** 七大页面导航，顺序即产品主流程 */
export const NAV_ITEMS: readonly NavItem[] = [
  { href: "/", label: "驾驶舱" },
  { href: "/world-model", label: "世界模型" },
  { href: "/intelligence-center", label: "情报中心" },
  { href: "/decision-lab", label: "决策实验室" },
  { href: "/agent-center", label: "Agent 中心" },
  { href: "/asset-center", label: "资产中心" },
  { href: "/notifications", label: "通知中心" },
] as const;

/** 通知类别 → 展示文案与配色（全站统一语义） */
export const NOTIFICATION_CATEGORY_MAP = {
  risk: { label: "风险提醒", text: "text-warning", bg: "bg-warning/15" },
  agent: { label: "Agent", text: "text-primary", bg: "bg-primary/15" },
  system: { label: "系统", text: "text-secondary", bg: "bg-secondary/15" },
} as const;

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

/** Agent 任务状态 → 展示文案与配色（run 枚举 running/succeeded/failed/cancelled 映射见 dashboard-data.tsx） */
export const AGENT_TASK_STATUS_MAP = {
  running: { label: "执行中", text: "text-primary", bg: "bg-primary/15" },
  waiting_auth: { label: "待授权", text: "text-warning", bg: "bg-warning/15" },
  done: { label: "已完成", text: "text-success", bg: "bg-success/15" },
  failed: { label: "失败", text: "text-danger", bg: "bg-danger/15" },
  cancelled: { label: "已取消", text: "text-muted", bg: "bg-secondary/15" },
} as const;

/** 事实可信状态 → 展示文案与配色（factStatus 暂定值，以 B 主笔的 extraction-schema.md 为准） */
export const FACT_STATUS_MAP = {
  confirmed: { label: "已确认", text: "text-success", bg: "bg-success/15" },
  inferred: { label: "推断", text: "text-warning", bg: "bg-warning/15" },
  unverified: { label: "待核实", text: "text-muted", bg: "bg-secondary/15" },
} as const;

/** 实体类型 → 展示文案（契约 5.1：person/asset/event/document/org） */
export const ENTITY_TYPE_MAP = {
  person: "人物",
  asset: "资产",
  event: "事件",
  document: "文档",
  org: "组织",
} as const;

/** 允许上传的文件类型与大小上限（契约 docs/api-specification.md 4.1：≤20MB，pdf/docx/xlsx/txt/md/png/jpg） */
export const UPLOAD_ACCEPT = ".pdf,.docx,.xlsx,.txt,.md,.png,.jpg,.jpeg";
export const UPLOAD_MAX_SIZE = 20 * 1024 * 1024;
/** 上传大小上限的展示文案（MB），避免组件层硬编码与常量不同步 */
export const UPLOAD_MAX_SIZE_MB = UPLOAD_MAX_SIZE / (1024 * 1024);
/** Mock 兜底开关：契约暂无对应接口的区块（驾驶舱目标进度/今日行动）是否启用演示数据。
 * 默认关闭（真实联调态，显示占位说明）；演示场景可设 NEXT_PUBLIC_USE_MOCK_DATA=true 开启。
 */
export const USE_MOCK_FALLBACK = process.env.NEXT_PUBLIC_USE_MOCK_DATA === "true";
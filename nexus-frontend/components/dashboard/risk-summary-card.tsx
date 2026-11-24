"use client";

import { RISK_LEVEL_MAP } from "@/lib/constants";
import { riskLevelIconOf } from "@/lib/icons";
import type { RiskItem } from "@/types";

/** 风险摘要卡：L2 语义卡——左侧 3px 等级色条 + 等级徽章 + 触发规则 + 原因 + 关联事实数
 * flat 面（无 blur）：风险列表可能同屏 N 张，玻璃 blur 会突破性能红线。
 */
export default function RiskSummaryCard({ risk }: { risk: RiskItem }) {
  const level = RISK_LEVEL_MAP[risk.level];
  const LevelIcon = riskLevelIconOf(risk.level);
  const stripColor =
    risk.level === "high" ? "border-l-danger" : risk.level === "medium" ? "border-l-warning" : "border-l-success";
  return (
    <div
      className={`card-flat shimmer rounded-xl border-l-[3px] p-4 ${stripColor} ${
        risk.level === "high" ? "glow-danger" : ""
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <p className="flex min-w-0 items-center gap-2 text-sm font-semibold text-content">
          <LevelIcon
            size={15}
            strokeWidth={2}
            className={`shrink-0 ${level.text}`}
            aria-hidden="true"
          />
          <span className="truncate">{risk.title}</span>
        </p>
        <span className={`flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-xs ${level.bg} ${level.text}`}>
          {level.label}
        </span>
      </div>
      {risk.ruleCode && (
        <span className="mt-2 inline-block rounded-full bg-accent/10 px-2.5 py-0.5 font-mono text-xs text-primary">
          规则 {risk.ruleCode}
        </span>
      )}
      <p className="mt-2 text-xs leading-relaxed text-secondary">{risk.reason}</p>
      {risk.relatedFactCount !== null && (
        <p className="mt-2 text-xs text-muted">关联事实 {risk.relatedFactCount} 条</p>
      )}
    </div>
  );
}
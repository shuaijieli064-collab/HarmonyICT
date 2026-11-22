"use client";

import { RISK_LEVEL_MAP } from "@/lib/constants";
import type { RiskItem } from "@/types";

/** 风险摘要卡：等级徽章 + 触发原因 + 关联事实数 */
export default function RiskSummaryCard({ risk }: { risk: RiskItem }) {
  const level = RISK_LEVEL_MAP[risk.level];
  return (
    <div className="rounded-xl border border-secondary/10 bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="truncate text-sm font-semibold text-content">{risk.title}</p>
        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs ${level.bg} ${level.text}`}>
          {level.label}
        </span>
      </div>
      <p className="mt-2 text-xs leading-relaxed text-secondary">{risk.reason}</p>
      {risk.relatedFactCount !== null && (
        <p className="mt-2 text-xs text-muted">关联事实 {risk.relatedFactCount} 条</p>
      )}
    </div>
  );
}
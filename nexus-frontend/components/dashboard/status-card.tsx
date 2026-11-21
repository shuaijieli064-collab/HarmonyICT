"use client";

import type { StatusMetric } from "@/types";

/** 驾驶舱状态指标卡 */
export default function StatusCard({ metric }: { metric: StatusMetric }) {
  return (
    <div className="rounded-xl border border-secondary/10 bg-card p-4 transition-colors hover:bg-card-hover">
      <p className="text-sm text-secondary">{metric.label}</p>
      <p className="mt-1 text-3xl font-bold tabular-nums text-content">{metric.value}</p>
      <p className="mt-1 text-xs text-muted">{metric.hint}</p>
    </div>
  );
}
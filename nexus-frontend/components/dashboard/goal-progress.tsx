"use client";

import type { GoalProgress } from "@/types";

/** 目标进度卡：标题 + 截止日期 + 进度条 */
export default function GoalProgressCard({ goal }: { goal: GoalProgress }) {
  return (
    <div className="rounded-xl border border-secondary/10 bg-card p-4">
      <div className="flex items-baseline justify-between gap-2">
        <p className="truncate text-sm font-semibold text-content">{goal.title}</p>
        <p className="shrink-0 text-xs text-muted">{goal.deadline} 截止</p>
      </div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-base">
        <div
          className="h-full rounded-full bg-primary"
          style={{ width: `${goal.percent}%` }}
          role="progressbar"
          aria-valuenow={goal.percent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={goal.title}
        />
      </div>
      <p className="mt-2 text-right text-xs tabular-nums text-secondary">{goal.percent}%</p>
    </div>
  );
}
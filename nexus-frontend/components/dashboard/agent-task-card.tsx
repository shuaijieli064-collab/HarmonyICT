"use client";

import { AGENT_TASK_STATUS_MAP } from "@/lib/constants";
import type { AgentTask } from "@/types";

/** Agent 任务卡：任务标题 + 执行状态徽章 + 更新时间 */
export default function AgentTaskCard({ task }: { task: AgentTask }) {
  const status = AGENT_TASK_STATUS_MAP[task.status];
  return (
    <div className="card-flat flex items-center justify-between gap-3 rounded-xl p-4">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-content">{task.title}</p>
        <p className="mt-1 text-xs text-muted">更新于 {task.updatedAt}</p>
      </div>
      <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs ${status.bg} ${status.text}`}>
        {status.label}
      </span>
    </div>
  );
}
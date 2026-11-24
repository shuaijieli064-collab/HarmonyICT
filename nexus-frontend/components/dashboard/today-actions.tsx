"use client";

import type { ActionItem } from "@/types";

/** 今日行动列表：来源标注 + 完成勾选（联调后接入任务状态更新接口） */
export default function TodayActions({ actions }: { actions: ActionItem[] }) {
  return (
    <ul className="space-y-3">
      {actions.map((action) => (
        <li
          key={action.id}
          className="card-flat flex items-start gap-3 rounded-xl p-4"
        >
          <input
            type="checkbox"
            checked={action.done}
            readOnly
            aria-label={action.title}
            className="mt-1 h-4 w-4 accent-primary"
          />
          <div className="min-w-0">
            <p
              className={`text-sm ${action.done ? "text-muted line-through" : "text-content"}`}
            >
              {action.title}
            </p>
            <p className="mt-1 text-xs text-muted">来自 {action.from}</p>
          </div>
        </li>
      ))}
    </ul>
  );
}
"use client";

import { cn } from "@/lib/utils";
import type { WorldEvent } from "@/types";

const EVENT_DOT_COLOR: Record<WorldEvent["kind"], string> = {
  fact: "bg-primary",
  risk: "bg-danger",
  action: "bg-success",
  source: "bg-accent",
};

/** 近期事件时间线：竖线 + 按事件类型着色的节点 */
export default function EventTimeline({ events }: { events: WorldEvent[] }) {
  return (
    <ol className="relative ml-1.5 border-l border-muted">
      {events.map((event) => (
        <li key={event.id} className="relative pb-5 pl-6 last:pb-0">
          <span
            className={cn(
              "absolute -left-[4.5px] top-1.5 h-2 w-2 rounded-full",
              EVENT_DOT_COLOR[event.kind]
            )}
            aria-hidden="true"
          />
          <p className="text-sm font-semibold text-content">{event.title}</p>
          <p className="mt-1 text-xs leading-relaxed text-secondary">{event.description}</p>
          <p className="mt-1 text-xs text-muted">{event.occurredAt}</p>
        </li>
      ))}
    </ol>
  );
}
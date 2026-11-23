"use client";

import { useEffect, useState } from "react";
import { insightTimeline, type TimelineItem } from "@/services/insight";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { RISK_LEVEL_MAP } from "@/lib/constants";
import type { RiskLevel } from "@/types";

const KIND_MAP: Record<string, { label: string; className: string }> = {
  deadline: { label: "截止", className: "text-danger bg-danger/15" },
  expiry: { label: "到期", className: "text-warning bg-warning/15" },
  event: { label: "事件", className: "text-primary bg-primary/15" },
};

/** 未来 30 天时间线（P3）：事实到期/任务截止/世界事件按日期投影 */
export default function TimelinePanel() {
  const authed = useIsLoggedIn();
  const [items, setItems] = useState<TimelineItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    insightTimeline(30)
      .then((data) => {
        if (!cancelled) setItems(data.items);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "时间线加载失败");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed]);

  if (!authed) {
    return <p className="text-sm text-warning">请先登录后查看未来时间线</p>;
  }

  if (error) {
    return <p className="text-sm text-danger">{error}</p>;
  }

  if (items === null) {
    return <p className="text-sm text-muted">正在投影未来 30 天…</p>;
  }

  return (
    <div className="space-y-2">
      {items.length === 0 ? (
        <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
          未来 30 天没有可投影的到期、截止或事件
        </p>
      ) : (
        <ul className="divide-y divide-secondary/10 rounded-xl border border-secondary/10 bg-card">
          {items.map((item, index) => {
            const kind = KIND_MAP[item.kind] ?? {
              label: item.kind,
              className: "text-muted bg-muted/15",
            };
            const level = item.level ? RISK_LEVEL_MAP[item.level as RiskLevel] : null;
            return (
              <li
                key={`${item.refType}-${item.refId}-${index}`}
                className="flex flex-wrap items-center gap-3 px-4 py-3"
              >
                <span className="font-mono text-xs text-muted">{item.date}</span>
                <span className={`rounded-full px-2 py-0.5 text-xs ${kind.className}`}>
                  {kind.label}
                </span>
                <span className="flex-1 text-sm text-content">{item.title}</span>
                {level && (
                  <span className={`rounded-full px-2 py-0.5 text-xs ${level.text} ${level.bg}`}>
                    {level.label}
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

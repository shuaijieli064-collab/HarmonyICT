"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { insightOverview, type EngineFamilySummary } from "@/services/insight";
import { useIsLoggedIn } from "@/hooks/use-auth";

/** 引擎族概览（P2）：未决风险按业务域分组的实时统计 */
export default function EngineOverview() {
  const authed = useIsLoggedIn();
  const [families, setFamilies] = useState<EngineFamilySummary[] | null>(null);
  const [totalOpen, setTotalOpen] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    insightOverview()
      .then((data) => {
        if (!cancelled) {
          setFamilies(data.families);
          setTotalOpen(data.totalOpen);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "引擎族概览加载失败");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed]);

  if (!authed) {
    return (
      <p className="text-sm text-warning">请先登录后查看洞察引擎概览</p>
    );
  }

  if (error) {
    return <p className="text-sm text-danger">{error}</p>;
  }

  if (families === null) {
    return <p className="text-sm text-muted">正在加载引擎族概览…</p>;
  }

  return (
    <div className="space-y-3">
      {families.length === 0 ? (
        <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
          当前无未决风险：各洞察引擎运行正常
        </p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {families.map((family) => (
            <div
              key={family.family}
              className="space-y-3 rounded-xl border border-secondary/10 bg-card p-4"
            >
              <div className="flex items-baseline justify-between">
                <h3 className="text-sm font-medium text-content">{family.label}</h3>
                <span className="text-2xl font-semibold text-content">{family.total}</span>
              </div>
              <div className="flex flex-wrap gap-2 text-xs">
                <span className="rounded-full bg-danger/15 px-2 py-0.5 text-danger">
                  高 {family.high}
                </span>
                <span className="rounded-full bg-warning/15 px-2 py-0.5 text-warning">
                  中 {family.medium}
                </span>
                <span className="rounded-full bg-success/15 px-2 py-0.5 text-success">
                  低 {family.low}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
      <p className="text-xs text-muted">
        共 {totalOpen} 条未决风险 ·
        <Link href="/intelligence-center" className="ml-1 text-primary hover:underline">
          前往情报中心处理 →
        </Link>
      </p>
    </div>
  );
}

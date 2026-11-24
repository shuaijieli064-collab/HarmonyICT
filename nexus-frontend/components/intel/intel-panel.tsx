"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { getIntelFeed, getIntelSummary } from "@/services/intel";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { RISK_LEVEL_MAP } from "@/lib/constants";
import type { IntelFeedItem, IntelSummary } from "@/types";

/** 情报中心面板（真实接口 GET /api/v1/intel）：
 * 汇总统计卡 + 统一情报流（风险/事件/Agent 执行），按时间倒序，type 筛选。
 */

const PAGE_SIZE = 15;

const FEED_TYPE_TABS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "all", label: "全部" },
  { value: "risk", label: "风险" },
  { value: "event", label: "事件" },
  { value: "agent", label: "Agent 动作" },
];

const FEED_TYPE_META: Record<string, { label: string; text: string; bg: string }> = {
  risk: { label: "风险", text: "text-danger", bg: "bg-danger/15" },
  event: { label: "事件", text: "text-primary", bg: "bg-primary/15" },
  agent: { label: "Agent", text: "text-success", bg: "bg-success/15" },
};

function formatTime(value: string | null): string {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

export default function IntelPanel() {
  const authed = useIsLoggedIn();
  const [summary, setSummary] = useState<IntelSummary | null>(null);
  const [items, setItems] = useState<IntelFeedItem[] | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [feedType, setFeedType] = useState("all");
  const [error, setError] = useState<string | null>(null);

  /** 纯数据拉取（不含 setState），type/page 变化共用 */
  const fetchFeed = useCallback(async (targetPage: number, targetType: string) => {
    const [nextSummary, nextFeed] = await Promise.all([
      getIntelSummary(),
      getIntelFeed({ page: targetPage, pageSize: PAGE_SIZE, type: targetType, days: 30 }),
    ]);
    return { summary: nextSummary, records: nextFeed.records, total: nextFeed.total };
  }, []);

  /** 加载与筛选切换：setState 位于异步回调（react-hooks/set-state-in-effect） */
  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchFeed(page, feedType)
      .then((data) => {
        if (cancelled) return;
        setError(null);
        setSummary(data.summary);
        setItems(data.records);
        setTotal(data.total);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "情报流加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchFeed, feedType, page]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后查看情报中心</p>
        <Link href="/login" className="inline-block text-sm text-primary hover:underline">
          前往登录 / 注册 →
        </Link>
      </div>
    );
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="space-y-6">
      {error && <p className="rounded-lg bg-danger/15 px-4 py-2 text-sm text-danger">{error}</p>}

      <section aria-label="情报汇总" className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">未关闭风险</p>
          <p className={`mt-1 text-2xl font-bold ${(summary?.openRiskCount ?? 0) > 0 ? "text-warning" : "text-success"}`}>
            {summary?.openRiskCount ?? "-"}
          </p>
          {summary && (
            <p className="mt-1 text-xs text-muted">
              高 {summary.riskByLevel.high ?? 0} · 中 {summary.riskByLevel.medium ?? 0} · 低{" "}
              {summary.riskByLevel.low ?? 0}
            </p>
          )}
        </div>
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">近 7 天事件</p>
          <p className="mt-1 text-2xl font-bold text-content">{summary?.recentEventCount ?? "-"}</p>
        </div>
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">待办任务</p>
          <p className="mt-1 text-2xl font-bold text-content">{summary?.pendingTaskCount ?? "-"}</p>
        </div>
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">近 7 天 Agent 执行</p>
          <p className="mt-1 text-2xl font-bold text-content">{summary?.recentAgentRunCount ?? "-"}</p>
        </div>
      </section>

      <div role="tablist" aria-label="情报类型筛选" className="flex gap-2">
        {FEED_TYPE_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={feedType === tab.value}
            onClick={() => {
              setFeedType(tab.value);
              setPage(1);
            }}
            className={`rounded-lg px-3 py-1.5 text-sm ${
              feedType === tab.value ? "bg-primary/15 text-primary" : "border border-secondary/10 text-secondary"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <section aria-label="情报流" className="space-y-3">
        {items === null ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-6 text-sm text-muted">加载中…</p>
        ) : items.length === 0 ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-6 text-sm text-muted">
            近 30 天暂无动态
          </p>
        ) : (
          items.map((item) => {
            const typeMeta = FEED_TYPE_META[item.type] ?? FEED_TYPE_META.event;
            const levelMeta = item.level && item.level in RISK_LEVEL_MAP
              ? RISK_LEVEL_MAP[item.level as keyof typeof RISK_LEVEL_MAP]
              : null;
            return (
              <article key={`${item.type}-${item.refId}`} className="rounded-xl border border-secondary/10 bg-card p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-md px-2 py-0.5 text-xs ${typeMeta.bg} ${typeMeta.text}`}>
                    {typeMeta.label}
                  </span>
                  {levelMeta && (
                    <span className={`rounded-md px-2 py-0.5 text-xs ${levelMeta.bg} ${levelMeta.text}`}>
                      {levelMeta.label}
                    </span>
                  )}
                  <time className="ml-auto text-xs text-muted">{formatTime(item.occurredAt)}</time>
                </div>
                <h3 className="mt-2 font-medium text-content">{item.title ?? "（无标题）"}</h3>
                {item.description && <p className="mt-1 text-sm text-secondary">{item.description}</p>}
              </article>
            );
          })
        )}
      </section>

      <nav aria-label="分页" className="flex items-center justify-center gap-4 text-sm">
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => setPage((current) => Math.max(1, current - 1))}
          className="rounded-lg border border-secondary/10 px-3 py-1 text-secondary disabled:opacity-40"
        >
          上一页
        </button>
        <span className="text-muted">
          {page} / {totalPages}
        </span>
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
          className="rounded-lg border border-secondary/10 px-3 py-1 text-secondary disabled:opacity-40"
        >
          下一页
        </button>
      </nav>
    </div>
  );
}

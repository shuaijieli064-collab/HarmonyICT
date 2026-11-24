"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { getAssetSummary, listAssets } from "@/services/assets";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { Asset, AssetSummary } from "@/types";

/** 资产中心面板（真实接口 GET /api/v1/assets）：
 * 汇总卡（总数 / 价值 / 未关闭风险 / 类型分布）+ 台账列表（实体 + 关键事实）。
 */

const PAGE_SIZE = 10;

function formatFactValue(objectValue: string, unit: string | null): string {
  return unit ? `${objectValue} ${unit}` : objectValue;
}

function formatTime(value: string | null): string {
  return value ? value.replace("T", " ").slice(0, 10) : "-";
}

const TYPE_LABELS: Record<string, string> = {
  person: "人物",
  asset: "资产",
  event: "事件",
  document: "文档",
  org: "组织",
};

export default function AssetPanel() {
  const authed = useIsLoggedIn();
  const [summary, setSummary] = useState<AssetSummary | null>(null);
  const [assets, setAssets] = useState<Asset[] | null>(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);

  /** 纯数据拉取（不含 setState），供 effect 复用（同 runs-panel 模式） */
  const fetchAssets = useCallback(async (targetPage: number) => {
    const [nextSummary, nextPage] = await Promise.all([
      getAssetSummary(),
      listAssets({ page: targetPage, pageSize: PAGE_SIZE }),
    ]);
    return { summary: nextSummary, records: nextPage.records, total: nextPage.total };
  }, []);

  /** 初始加载与翻页：setState 位于异步回调 */
  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchAssets(page)
      .then((data) => {
        if (cancelled) return;
        setError(null);
        setSummary(data.summary);
        setAssets(data.records);
        setTotal(data.total);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "资产数据加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchAssets, page]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后查看资产台账</p>
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

      <section aria-label="资产汇总" className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">资产总数</p>
          <p className="mt-1 text-2xl font-bold text-content">{summary?.totalAssets ?? "-"}</p>
        </div>
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">价值汇总</p>
          <p className="mt-1 text-2xl font-bold text-content">
            {summary ? formatFactValue(String(summary.totalValue), summary.valueUnit) : "-"}
          </p>
        </div>
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">未关闭风险</p>
          <p className={`mt-1 text-2xl font-bold ${(summary?.openRiskCount ?? 0) > 0 ? "text-warning" : "text-success"}`}>
            {summary?.openRiskCount ?? "-"}
          </p>
        </div>
        <div className="rounded-xl border border-secondary/10 bg-card p-4">
          <p className="text-xs text-muted">类型分布</p>
          <p className="mt-1 line-clamp-2 text-sm text-secondary">
            {summary
              ? Object.entries(summary.byType)
                  .map(([type, count]) => `${TYPE_LABELS[type] ?? type}×${count}`)
                  .join("、") || "暂无数据"
              : "-"}
          </p>
        </div>
      </section>

      <section aria-label="资产台账" className="space-y-3">
        {assets === null ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-6 text-sm text-muted">加载中…</p>
        ) : assets.length === 0 ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-6 text-sm text-muted">
            暂无资产：先上传资料或在世界模型中创建实体
          </p>
        ) : (
          assets.map((asset) => (
            <article key={asset.id} className="rounded-xl border border-secondary/10 bg-card p-4">
              <div className="flex items-center justify-between gap-2">
                <h3 className="font-medium text-content">{asset.name}</h3>
                <span className="rounded-md bg-primary/15 px-2 py-0.5 text-xs text-primary">
                  {TYPE_LABELS[asset.entityType] ?? asset.entityType}
                </span>
              </div>
              {asset.facts.length > 0 && (
                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 md:grid-cols-3">
                  {asset.facts.map((fact) => (
                    <div key={fact.factId} className="min-w-0">
                      <dt className="truncate text-xs text-muted">{fact.predicate}</dt>
                      <dd className="truncate text-sm text-secondary">
                        {formatFactValue(fact.objectValue, fact.unit)}
                        {fact.validUntil && (
                          <span className="ml-1 text-xs text-warning">（至 {formatTime(fact.validUntil)}）</span>
                        )}
                      </dd>
                    </div>
                  ))}
                </dl>
              )}
            </article>
          ))
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

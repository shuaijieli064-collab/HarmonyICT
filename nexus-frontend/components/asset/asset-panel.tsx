"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { listEntities, listFacts } from "@/services/world";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { ENTITY_TYPE_MAP, FACT_STATUS_MAP } from "@/lib/constants";
import type { Entity, EntityType, Fact } from "@/types";

/** 资产中心面板（真实接口）：按实体聚合事实形成资产台账。
 * 资产卡 = 实体（名称/类型）+ 名下事实（谓词、取值、有效期）；
 * validUntil 距今 ≤30 天的事实标记「即将到期」倒计时，与规则引擎的
 * CERT_EXPIRY_30D 类风险互相印证（前端展示层，结论仍由规则引擎产出）。
 */

/** 有效期倒计时 → 徽章文案与配色（确定性展示规则） */
function expiryBadge(validUntil: string): { text: string; className: string } | null {
  const due = new Date(validUntil).getTime();
  if (Number.isNaN(due)) return null;
  const days = Math.ceil((due - Date.now()) / 86400000);
  if (days < 0) return { text: `已到期 ${-days} 天`, className: "bg-danger/15 text-danger" };
  if (days <= 30) return { text: `${days} 天后到期`, className: "bg-warning/15 text-warning" };
  return { text: `${days} 天后到期`, className: "bg-secondary/15 text-muted" };
}

interface AssetGroup {
  entity: Entity;
  facts: Fact[];
  /** 名下最近的有效期（用于卡片排序提示） */
  nearestExpiryDays: number | null;
}

function groupByEntity(entities: Entity[], facts: Fact[]): AssetGroup[] {
  const factBySubject = new Map<string, Fact[]>();
  for (const fact of facts) {
    const list = factBySubject.get(fact.subjectId) ?? [];
    list.push(fact);
    factBySubject.set(fact.subjectId, list);
  }

  return entities.map((entity) => {
    const entityFacts = factBySubject.get(entity.id) ?? [];
    let nearestExpiryDays: number | null = null;
    for (const fact of entityFacts) {
      if (!fact.validUntil) continue;
      const due = new Date(fact.validUntil).getTime();
      if (Number.isNaN(due)) continue;
      const days = Math.ceil((due - Date.now()) / 86400000);
      if (nearestExpiryDays === null || days < nearestExpiryDays) nearestExpiryDays = days;
    }
    return { entity, facts: entityFacts, nearestExpiryDays };
  });
}

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | {
      phase: "ready";
      groups: AssetGroup[];
      stats: { entityTotal: number; factTotal: number; expiringTotal: number };
    };

export default function AssetPanel() {
  const authed = useIsLoggedIn();
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  const fetchAssets = useCallback(async (): Promise<LoadState> => {
    const [entityPage, factPage] = await Promise.all([
      listEntities({ page: 1, pageSize: 100 }),
      listFacts({ page: 1, pageSize: 200 }),
    ]);
    const groups = groupByEntity(entityPage.records, factPage.records);
    const expiringTotal = groups.filter(
      (group) => group.nearestExpiryDays !== null && group.nearestExpiryDays <= 30
    ).length;
    return {
      phase: "ready",
      groups,
      stats: {
        entityTotal: entityPage.total,
        factTotal: factPage.total,
        expiringTotal,
      },
    };
  }, []);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchAssets()
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ phase: "error", message: err instanceof Error ? err.message : "资产数据加载失败" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchAssets]);

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

  if (state.phase === "loading") {
    return <p className="text-sm text-muted">正在加载资产台账…</p>;
  }
  if (state.phase === "error") {
    return <p className="text-sm text-danger">{state.message}</p>;
  }

  const statItems = [
    { label: "资产/实体", value: String(state.stats.entityTotal) },
    { label: "关联事实", value: String(state.stats.factTotal) },
    { label: "30 天内到期", value: String(state.stats.expiringTotal) },
  ];

  return (
    <div className="space-y-6">
      <section aria-label="台账统计" className="grid grid-cols-3 gap-4">
        {statItems.map((item) => (
          <div key={item.label} className="rounded-xl border border-secondary/10 bg-card p-4">
            <p className="text-2xl font-bold text-content">{item.value}</p>
            <p className="mt-1 text-xs text-muted">{item.label}</p>
          </div>
        ))}
      </section>

      <section aria-label="资产台账列表" className="space-y-3">
        <h2 className="text-base font-semibold text-content">资产台账</h2>
        {state.groups.length === 0 ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
            暂无资产：上传发票 / 订阅凭证完成抽取后，这里会按实体自动归集出台账
          </p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {state.groups.map(({ entity, facts, nearestExpiryDays }) => (
              <div key={entity.id} className="space-y-2 rounded-xl border border-secondary/10 bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <p className="truncate text-sm font-semibold text-content">{entity.name}</p>
                  <span className="shrink-0 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs text-primary">
                    {ENTITY_TYPE_MAP[entity.entityType as EntityType] ?? entity.entityType}
                  </span>
                </div>
                {nearestExpiryDays !== null && nearestExpiryDays <= 30 && (
                  <span className="inline-block rounded-full bg-warning/15 px-2.5 py-0.5 text-xs text-warning">
                    有事项 {nearestExpiryDays < 0 ? "已过期" : `${nearestExpiryDays} 天内到期`}
                  </span>
                )}
                {facts.length === 0 ? (
                  <p className="text-xs text-muted">名下暂无事实，等待抽取或补录</p>
                ) : (
                  <ul className="space-y-1.5">
                    {facts.map((fact) => {
                      const badge = fact.validUntil ? expiryBadge(fact.validUntil) : null;
                      const status = FACT_STATUS_MAP[fact.factStatus];
                      return (
                        <li key={fact.id} className="flex flex-wrap items-center gap-2 text-xs leading-relaxed">
                          <span className="text-secondary">{fact.predicate}</span>
                          <span className="text-content">
                            ：{fact.objectValue}
                            {fact.unit ? ` ${fact.unit}` : ""}
                          </span>
                          {badge && (
                            <span className={`rounded-full px-2 py-0.5 ${badge.className}`}>{badge.text}</span>
                          )}
                          <span className={`rounded-full px-2 py-0.5 ${status.bg} ${status.text}`}>
                            {status.label}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                )}
                <p className="text-xs text-muted">台账 ID #{entity.id}</p>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
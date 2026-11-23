"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { listEntities, parseEntityAttributes } from "@/services/world";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { Entity } from "@/types";

const ENTITY_TYPE_MAP: Record<string, { label: string; className: string }> = {
  asset: { label: "资产", className: "text-primary bg-primary/15" },
  person: { label: "人物", className: "text-success bg-success/15" },
  document: { label: "文档", className: "text-warning bg-warning/15" },
  org: { label: "组织", className: "text-secondary bg-secondary/15" },
  event: { label: "事件", className: "text-muted bg-muted/15" },
};

const FILTERS: { key: string; label: string }[] = [
  { key: "all", label: "全部" },
  { key: "asset", label: "资产" },
  { key: "person", label: "人物" },
  { key: "document", label: "文档" },
  { key: "org", label: "组织" },
  { key: "event", label: "事件" },
];

/** 资产台账（已接真实接口 /api/v1/entities）：统一展示实体及其关键属性 */
export default function AssetTable() {
  const authed = useIsLoggedIn();
  const [entities, setEntities] = useState<Entity[] | null>(null);
  const [filter, setFilter] = useState("all");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    listEntities({ page: 1, pageSize: 100 })
      .then((page) => {
        if (!cancelled) setEntities(page.records);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "实体列表加载失败，请确认后端 8080 已启动");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed]);

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

  const visible = (entities ?? []).filter((e) => filter === "all" || e.entityType === filter);

  return (
    <div className="space-y-4">
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex flex-wrap items-center gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            onClick={() => setFilter(f.key)}
            className={`rounded-lg border px-3 py-1 text-xs transition-colors ${
              filter === f.key
                ? "border-primary/40 bg-primary/15 text-primary"
                : "border-secondary/10 text-secondary hover:text-content"
            }`}
          >
            {f.label}
          </button>
        ))}
        <span className="ml-auto text-xs text-muted">
          {entities === null ? "加载中…" : `共 ${visible.length} 个实体`}
        </span>
      </div>

      {entities !== null && visible.length === 0 ? (
        <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
          暂无实体：在情报中心上传资料完成抽取后，实体将自动进入台账
        </p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visible.map((entity) => {
            const typeInfo = ENTITY_TYPE_MAP[entity.entityType] ?? {
              label: entity.entityType,
              className: "text-muted bg-muted/15",
            };
            const attrs = Object.entries(parseEntityAttributes(entity.attributes)).slice(0, 4);
            return (
              <div
                key={entity.id}
                className="space-y-3 rounded-xl border border-secondary/10 bg-card p-4"
              >
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-sm font-medium leading-snug text-content">{entity.name}</h3>
                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${typeInfo.className}`}>
                    {typeInfo.label}
                  </span>
                </div>
                {attrs.length === 0 ? (
                  <p className="text-xs text-muted">暂无结构化属性</p>
                ) : (
                  <ul className="space-y-1">
                    {attrs.map(([key, value]) => (
                      <li key={key} className="text-xs leading-relaxed text-secondary">
                        <span className="text-muted">{key}</span>
                        ：{typeof value === "object" ? JSON.stringify(value) : String(value)}
                      </li>
                    ))}
                  </ul>
                )}
                <p className="text-xs text-muted">
                  建档时间：{new Date(entity.createdAt).toLocaleString("zh-CN")}
                </p>
              </div>
            );
          })}
        </div>
      )}

      <p className="text-xs text-muted">
        需要查看实体间关系？前往{" "}
        <Link href="/world-model" className="text-primary hover:underline">
          世界模型图谱 →
        </Link>
      </p>
    </div>
  );
}

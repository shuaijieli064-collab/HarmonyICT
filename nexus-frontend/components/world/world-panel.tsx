"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { createEntity, createFact, listEntities, listFacts, parseEntityAttributes } from "@/services/world";
import { ApiError } from "@/lib/http";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { ENTITY_TYPE_MAP, FACT_STATUS_MAP } from "@/lib/constants";
import type { Entity, EntityType, Fact } from "@/types";

/** 世界模型面板（真实接口）：实体列表 + 创建表单 + 事实列表。
 * 创建实体重名返回契约 20104，界面区分提示「已存在同名实体」；
 * 事实列表用于联调时核对抽取轮询的落库结果（confidence/factStatus）。
 */

const ENTITY_TYPE_OPTIONS: EntityType[] = ["person", "asset", "event", "document", "org"];

function formatAttributes(entity: Entity): string {
  const attributes = parseEntityAttributes(entity.attributes);
  const entries = Object.entries(attributes);
  if (entries.length === 0) return "-";
  return entries.map(([key, value]) => `${key}: ${String(value)}`).join(" · ");
}

export default function WorldPanel() {
  const authed = useIsLoggedIn();
  const [entities, setEntities] = useState<Entity[] | null>(null);
  const [facts, setFacts] = useState<Fact[] | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [entityType, setEntityType] = useState<EntityType>("asset");
  const [name, setName] = useState("");
  const [attributesText, setAttributesText] = useState("");
  const [busy, setBusy] = useState(false);
  const [factFormOpen, setFactFormOpen] = useState(false);
  const [factSubject, setFactSubject] = useState("");
  const [factPredicate, setFactPredicate] = useState("");
  const [factObject, setFactObject] = useState("");
  const [factBusy, setFactBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const fetchEntities = useCallback(async (): Promise<Entity[]> => {
    const page = await listEntities({ page: 1, pageSize: 20 });
    return page.records;
  }, []);

  const fetchFacts = useCallback(async (): Promise<Fact[]> => {
    const page = await listFacts({ page: 1, pageSize: 10 });
    return page.records;
  }, []);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    Promise.all([fetchEntities(), fetchFacts()])
      .then(([entityRecords, factRecords]) => {
        if (!cancelled) {
          setEntities(entityRecords);
          setFacts(factRecords);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "世界模型加载失败，请确认后端 8080 已启动");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchEntities, fetchFacts]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后查看世界模型</p>
        <Link href="/login" className="inline-block text-sm text-primary hover:underline">
          前往登录 / 注册 →
        </Link>
      </div>
    );
  }

  const onCreate = () => {
    const trimmedName = name.trim();
    if (!trimmedName) return;
    let attributes: Record<string, unknown> = {};
    if (attributesText.trim()) {
      try {
        const parsed = JSON.parse(attributesText) as unknown;
        if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
          setError("attributes 需为 JSON 对象，如 {\"amount\": 50000}");
          return;
        }
        attributes = parsed as Record<string, unknown>;
      } catch {
        setError("attributes JSON 格式不合法");
        return;
      }
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    createEntity({ entityType, name: trimmedName, attributes })
      .then((created) => {
        setNotice(`实体已创建：${created.name}（#${created.id}）`);
        setName("");
        setAttributesText("");
        setFormOpen(false);
        return fetchEntities().then(setEntities);
      })
      .catch((err: unknown) => {
        // 契约 20104：实体重名，区分提示
        if (err instanceof ApiError && err.code === 20104) {
          setError(`已存在同名实体「${trimmedName}」，请换一个名称或直接复用`);
        } else {
          setError(err instanceof Error ? err.message : "创建实体失败");
        }
      })
      .finally(() => setBusy(false));
  };

  const onCreateFact = () => {
    if (!factSubject || !factPredicate.trim() || !factObject.trim()) return;
    setFactBusy(true);
    setError(null);
    setNotice(null);
    createFact({
      subjectId: factSubject,
      predicate: factPredicate.trim(),
      objectValue: factObject.trim(),
    })
      .then((created) => {
        setNotice(`事实已录入：${created.predicate} = ${created.objectValue}（#${created.id}）`);
        setFactPredicate("");
        setFactObject("");
        setFactFormOpen(false);
        return fetchFacts().then(setFacts);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "录入事实失败");
      })
      .finally(() => setFactBusy(false));
  };

  return (
    <div className="space-y-6">
      {error && <p className="text-sm text-danger">{error}</p>}
      {notice && <p className="text-sm text-success">{notice}</p>}

      <section aria-label="实体" className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-content">实体</h2>
          <button
            type="button"
            onClick={() => {
              setFormOpen((prev) => !prev);
              setError(null);
              setNotice(null);
            }}
            className="text-sm text-primary hover:underline"
          >
            {formOpen ? "收起表单" : "+ 创建实体"}
          </button>
        </div>

        {formOpen && (
          <div className="grid gap-3 rounded-xl border border-secondary/10 bg-card p-4 md:grid-cols-3">
            <select
              value={entityType}
              onChange={(e) => setEntityType(e.target.value as EntityType)}
              className="rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content"
            >
              {ENTITY_TYPE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {ENTITY_TYPE_MAP[option]}
                </option>
              ))}
            </select>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="实体名称，如：XX基金"
              className="rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content outline-none focus:border-primary"
            />
            <div className="flex gap-2">
              <input
                value={attributesText}
                onChange={(e) => setAttributesText(e.target.value)}
                placeholder='attributes JSON，如 {"amount": 50000}'
                className="min-w-0 flex-1 rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content outline-none focus:border-primary"
              />
              <button
                type="button"
                onClick={onCreate}
                disabled={busy || !name.trim()}
                className="shrink-0 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60"
              >
                创建
              </button>
            </div>
          </div>
        )}

        {entities === null ? (
          <p className="text-sm text-muted">正在加载实体…</p>
        ) : entities.length === 0 ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
            暂无实体：上传资料抽取入库会自动创建，也可手动创建
          </p>
        ) : (
          <div className="overflow-hidden rounded-xl border border-secondary/10">
            <table className="w-full text-left text-sm">
              <thead className="bg-card text-xs text-secondary">
                <tr>
                  <th className="px-4 py-2.5 font-medium">类型</th>
                  <th className="px-4 py-2.5 font-medium">名称</th>
                  <th className="px-4 py-2.5 font-medium">扩展属性</th>
                  <th className="px-4 py-2.5 font-medium">ID</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-secondary/10">
                {entities.map((entity) => (
                  <tr key={entity.id} className="bg-card">
                    <td className="px-4 py-2.5 text-xs text-primary">
                      {ENTITY_TYPE_MAP[entity.entityType as EntityType] ?? entity.entityType}
                    </td>
                    <td className="px-4 py-2.5 text-content">{entity.name}</td>
                    <td className="px-4 py-2.5 text-xs text-secondary">{formatAttributes(entity)}</td>
                    <td className="px-4 py-2.5 text-xs text-muted">#{entity.id}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section aria-label="事实" className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-content">最近事实</h2>
          <button
            type="button"
            onClick={() => {
              setFactFormOpen((prev) => !prev);
              setError(null);
              setNotice(null);
            }}
            className="text-sm text-primary hover:underline"
          >
            {factFormOpen ? "收起表单" : "+ 录入事实"}
          </button>
        </div>

        {factFormOpen && (
          <div className="grid gap-3 rounded-xl border border-secondary/10 bg-card p-4 md:grid-cols-4">
            <select
              value={factSubject}
              onChange={(e) => setFactSubject(e.target.value)}
              className="rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content"
              aria-label="所属实体"
            >
              <option value="">选择实体…</option>
              {entities?.map((entity) => (
                <option key={entity.id} value={entity.id}>
                  {entity.name}（#{entity.id}）
                </option>
              ))}
            </select>
            <input
              value={factPredicate}
              onChange={(e) => setFactPredicate(e.target.value)}
              placeholder="谓词，如：certificate_expiry"
              className="rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content outline-none focus:border-primary"
            />
            <div className="flex gap-2 md:col-span-2">
              <input
                value={factObject}
                onChange={(e) => setFactObject(e.target.value)}
                placeholder="取值，如：2026-12-31"
                className="min-w-0 flex-1 rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content outline-none focus:border-primary"
              />
              <button
                type="button"
                onClick={onCreateFact}
                disabled={factBusy || !factSubject || !factPredicate.trim() || !factObject.trim()}
                className="shrink-0 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60"
              >
                录入
              </button>
            </div>
          </div>
        )}

        {facts === null ? (
          <p className="text-sm text-muted">正在加载事实…</p>
        ) : facts.length === 0 ? (
          <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
            暂无事实：上传资料完成抽取后，这里会展示落库的事实（含置信度与可信状态）
          </p>
        ) : (
          <ul className="space-y-2">
            {facts.map((fact) => {
              const status = FACT_STATUS_MAP[fact.factStatus];
              return (
                <li
                  key={fact.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-secondary/10 bg-card p-3"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm text-content">
                      <span className="text-secondary">{fact.predicate}</span>：{fact.objectValue}
                      {fact.unit ? ` ${fact.unit}` : ""}
                    </p>
                    <p className="mt-0.5 text-xs text-muted">
                      置信度 {fact.confidence}
                      {fact.evidence ? ` · ${fact.evidence}` : ""}
                    </p>
                  </div>
                  <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs ${status?.bg ?? ""} ${status?.text ?? ""}`}>
                    {status?.label ?? fact.factStatus}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
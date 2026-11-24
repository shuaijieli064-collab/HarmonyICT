"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { listEntities, listFacts } from "@/services/world";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { EChartsType } from "echarts";
import type { Entity, EntityType, Fact } from "@/types";

/** 实体关系图谱（ECharts graph 力导向）：实体为一级节点（按类型着色），
 * 事实为二级节点（predicate 命名），边为 事实.subjectId → 事实.id 的放射结构，
 * 直观呈现「实体 —谓词— 取值」知识三元组。echarts 动态 import 避免 SSR 引用窗口对象。
 */

const CATEGORY_LABELS: Record<EntityType | "fact", string> = {
  person: "人物",
  asset: "资产",
  event: "事件",
  document: "文档",
  org: "组织",
  fact: "事实",
};

/** 靛紫系对齐「暗夜玻璃」主题（globals.css --accent 系） */
const CATEGORY_COLORS = ["#6366f1", "#a855f7", "#60a5fa", "#34d399", "#fbbf24", "#6b7280"];

const ENTITY_TYPES: EntityType[] = ["person", "asset", "event", "document", "org"];

function buildGraph(entities: Entity[], facts: Fact[]) {
  const entityById = new Map(entities.map((entity) => [entity.id, entity]));
  const nodes: Array<{
    id: string;
    name: string;
    category: number;
    symbolSize: number;
    label?: { show: boolean };
    value?: string;
  }> = [];
  const links: Array<{ source: string; target: string; label?: { show: boolean; formatter: string } }> = [];

  for (const entity of entities) {
    const categoryIndex = ENTITY_TYPES.indexOf(entity.entityType as EntityType);
    nodes.push({
      id: `entity:${entity.id}`,
      name: entity.name,
      category: categoryIndex >= 0 ? categoryIndex : 0,
      symbolSize: 34,
      label: { show: true },
    });
  }

  for (const fact of facts) {
    if (!entityById.has(fact.subjectId)) continue;
    nodes.push({
      id: `fact:${fact.id}`,
      name: `${fact.predicate}\n${fact.objectValue}`,
      category: ENTITY_TYPES.length,
      symbolSize: 16,
      label: { show: true },
      value: String(fact.confidence),
    });
    links.push({
      source: `entity:${fact.subjectId}`,
      target: `fact:${fact.id}`,
      label: { show: false, formatter: fact.predicate },
    });
  }

  return {
    categories: [...ENTITY_TYPES, "fact"].map((key) => ({
      name: CATEGORY_LABELS[key as EntityType | "fact"],
      itemStyle: { color: CATEGORY_COLORS[["person", "asset", "event", "document", "org", "fact"].indexOf(key)] },
    })),
    nodes,
    links,
  };
}

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "empty" }
  | { phase: "ready"; entities: Entity[]; facts: Fact[] };

export default function EntityGraph() {
  const authed = useIsLoggedIn();
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartsType | null>(null);
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  const fetchGraphData = useCallback(async (): Promise<LoadState> => {
    const [entityPage, factPage] = await Promise.all([
      listEntities({ page: 1, pageSize: 100 }),
      listFacts({ page: 1, pageSize: 100 }),
    ]);
    if (entityPage.records.length === 0) return { phase: "empty" };
    return { phase: "ready", entities: entityPage.records, facts: factPage.records };
  }, []);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchGraphData()
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ phase: "error", message: err instanceof Error ? err.message : "图谱数据加载失败" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchGraphData]);

  useEffect(() => {
    if (state.phase !== "ready" || !containerRef.current) return;
    let disposed = false;
    let resizeHandler: (() => void) | null = null;

    void (async () => {
      const echarts = await import("echarts");
      if (disposed || !containerRef.current) return;
      const chart = echarts.init(containerRef.current);
      chartRef.current = chart;
      const graph = buildGraph(state.entities, state.facts);
      chart.setOption({
        tooltip: { show: true, formatter: (params: { data?: { value?: string } }) => params.data?.value ?? "" },
        legend: [{ data: graph.categories.map((category) => category.name), textStyle: { color: "#a1a1aa" } }],
        series: [
          {
            type: "graph",
            layout: "force",
            roam: true,
            draggable: true,
            categories: graph.categories,
            data: graph.nodes,
            links: graph.links,
            force: { repulsion: 220, edgeLength: 90, gravity: 0.08 },
            label: { color: "#ededef", fontSize: 11 },
            lineStyle: { color: "#3f3f46", curveness: 0.1 },
            emphasis: { focus: "adjacency", lineStyle: { width: 2 } },
          },
        ],
      });
      resizeHandler = () => chart.resize();
      window.addEventListener("resize", resizeHandler);
    })();

    return () => {
      disposed = true;
      if (resizeHandler) window.removeEventListener("resize", resizeHandler);
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, [state]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后查看图谱</p>
        <Link href="/login" className="inline-block text-sm text-primary hover:underline">
          前往登录 / 注册 →
        </Link>
      </div>
    );
  }

  if (state.phase === "loading") {
    return (
      <div className="flex min-h-80 items-center justify-center rounded-xl border border-secondary/10 bg-card">
        <p className="text-sm text-muted">正在加载图谱数据…</p>
      </div>
    );
  }
  if (state.phase === "error") {
    return (
      <div className="flex min-h-80 items-center justify-center rounded-xl border border-secondary/10 bg-card">
        <p className="text-sm text-danger">{state.message}</p>
      </div>
    );
  }
  if (state.phase === "empty") {
    return (
      <div className="flex min-h-80 flex-col items-center justify-center gap-2 rounded-xl border border-secondary/10 bg-card">
        <p className="text-sm text-muted">暂无实体节点</p>
        <p className="text-xs text-muted">上传资料完成抽取或手动创建实体后，图谱将自动呈现</p>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className="h-96 rounded-xl border border-secondary/10 bg-card"
      aria-label="实体关系图谱"
      role="img"
    />
  );
}
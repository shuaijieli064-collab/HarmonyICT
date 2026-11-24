"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { EChartsType } from "echarts";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { getWorldGraph } from "@/services/world";
import type { EntityType, WorldGraphLink, WorldGraphNode, WorldGraphResponse } from "@/types";

/** 实体关系图谱（体量扩充第二波已切聚合接口 /api/v1/world/graph）：
 * 节点=实体/事实/事件/未解决风险；边=事实归属、事件归属、同源资料、风险关联。
 * 渲染层沿用 ECharts 力导向布局。
 */

const CATEGORY_KEYS = ["person", "asset", "document", "org", "event_type", "fact", "event", "risk"] as const;

const CATEGORY_LABELS: Record<string, string> = {
  person: "人物",
  asset: "资产",
  document: "文档",
  org: "组织",
  event_type: "其他实体",
  fact: "事实",
  event: "事件",
  risk: "风险",
};

const CATEGORY_COLORS = ["#6366f1", "#a855f7", "#60a5fa", "#34d399", "#6b7280", "#fbbf24", "#38bdf8", "#f87171"];

const KIND_SIZE: Record<string, number> = { entity: 34, fact: 14, event: 20, risk: 26 };

const EDGE_COLORS: Record<string, string> = {
  fact_of: "#3f3f46",
  event_of: "#38bdf8",
  co_source: "#fbbf24",
  risk_on: "#f87171",
};

const ENTITY_TYPES: EntityType[] = ["person", "asset", "document", "org"];

function categoryKeyOf(node: WorldGraphNode): string {
  if (node.kind === "entity") {
    return ENTITY_TYPES.includes(node.category as EntityType) ? node.category : "event_type";
  }
  return CATEGORY_KEYS.includes(node.category as (typeof CATEGORY_KEYS)[number]) ? node.category : "fact";
}

function buildGraph(graph: WorldGraphResponse) {
  const nodes = graph.nodes.map((node) => ({
    id: node.id,
    name: node.name,
    category: CATEGORY_KEYS.indexOf(categoryKeyOf(node) as (typeof CATEGORY_KEYS)[number]),
    symbolSize: KIND_SIZE[node.kind] ?? 16,
    label: { show: node.kind !== "fact" },
    value: node.meta || undefined,
  }));

  const links = graph.links.map((link: WorldGraphLink) => ({
    source: link.source,
    target: link.target,
    lineStyle: {
      color: EDGE_COLORS[link.kind] ?? "#3f3f46",
      width: link.kind === "fact_of" ? 1 : 2,
      type: link.kind === "co_source" ? "dashed" : "solid",
    },
    label: { show: link.kind !== "fact_of", formatter: link.label, fontSize: 9 },
  }));

  return {
    categories: CATEGORY_KEYS.map((key, index) => ({
      name: CATEGORY_LABELS[key],
      itemStyle: { color: CATEGORY_COLORS[index] },
    })),
    nodes,
    links,
  };
}

/** 读取设计 token：ECharts 需要stringLiteral 颜色，运行时从 CSS 变量取（双主题跟随） */
function cssVar(name: string, fallback: string): string {
  if (typeof window === "undefined") return fallback;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "empty" }
  | { phase: "ready"; graph: WorldGraphResponse };

export default function EntityGraph() {
  const authed = useIsLoggedIn();
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartsType | null>(null);
  const [state, setState] = useState<LoadState>({ phase: "loading" });
  const [ready, setReady] = useState(false);

  const fetchGraphData = useCallback(async (): Promise<LoadState> => {
    const graph = await getWorldGraph();
    if (graph.nodes.length === 0) return { phase: "empty" };
    return { phase: "ready", graph };
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
      const graph = buildGraph(state.graph);
      chart.setOption({
        tooltip: { show: true, formatter: (params: { data?: { value?: string } }) => params.data?.value ?? "" },
        legend: [{
          data: graph.categories.map((category) => category.name),
          textStyle: { color: cssVar("--text-secondary", "#a1a1aa") },
        }],
        // 玻璃画布透明：不遮住背景环境光
        backgroundColor: "transparent",
        animationDuration: 400,
        animationEasing: "cubicOut",
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
            label: { color: cssVar("--text-primary", "#ededef"), fontSize: 11 },
            lineStyle: { curveness: 0.1 },
            emphasis: { focus: "adjacency", lineStyle: { width: 3 } },
          },
        ],
      });
      resizeHandler = () => chart.resize();
      window.addEventListener("resize", resizeHandler);
      // force 布局前 450ms 节点从随机位置展开，遮罩淡入避免"炸开"观感
      const revealTimer = setTimeout(() => setReady(true), 450);
      return () => clearTimeout(revealTimer);
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
      <div className="min-h-80 rounded-xl border border-secondary/10 bg-card p-6">
        <div className="flex h-full items-center justify-center">
          <div className="w-full max-w-md space-y-4">
            <div className="skeleton h-24 w-24 rounded-full" />
            <div className="skeleton h-4 w-2/3 rounded-lg" />
            <div className="skeleton h-4 w-1/2 rounded-lg" />
            <div className="skeleton h-4 w-3/5 rounded-lg" />
          </div>
        </div>
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
      className={`h-80 rounded-xl border border-secondary/10 bg-card transition-opacity duration-500 sm:h-96 ${
        ready ? "opacity-100" : "opacity-0"
      }`}
      aria-label="实体关系图谱"
      role="img"
    />
  );
}

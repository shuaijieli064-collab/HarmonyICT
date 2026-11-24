"use client";

import { useEffect, useRef, useState } from "react";
import type { EChartsType } from "echarts";
import { CHART_COLORS } from "@/lib/constants";
import { getRiskTrend } from "@/services/risks";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { RiskTrendPoint } from "@/types";

/** 风险趋势折线图（扩展 B 组）：近 7/14/30 天 open 风险等级分布快照。
 * ECharts 颜色与全站图表色板一致（CHART_COLORS）；透明画布融入玻璃背景。
 */

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "empty" }
  | { phase: "ready"; points: RiskTrendPoint[] };

const PERIODS = [7, 14, 30] as const;

export default function RiskTrend() {
  const authed = useIsLoggedIn();
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartsType | null>(null);
  const [days, setDays] = useState<number>(7);
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    getRiskTrend(days)
      .then((points) => {
        if (cancelled) return;
        const hasData = points.some((p) => p.openCount > 0);
        setState(hasData ? { phase: "ready", points } : { phase: "empty" });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ phase: "error", message: err instanceof Error ? err.message : "趋势加载失败" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, days]);

  useEffect(() => {
    if (state.phase !== "ready" || !containerRef.current) return;
    let disposed = false;
    let resizeHandler: (() => void) | null = null;

    void (async () => {
      const echarts = await import("echarts");
      if (disposed || !containerRef.current) return;
      const chart = echarts.init(containerRef.current);
      chartRef.current = chart;
      const points = state.points;
      const axisColor =
        getComputedStyle(document.documentElement).getPropertyValue("--text-secondary").trim() || "#a1a1aa";
      chart.setOption({
        backgroundColor: "transparent",
        animationDuration: 400,
        animationEasing: "cubicOut",
        tooltip: { trigger: "axis" },
        legend: { data: ["高风险", "需关注", "正常"], textStyle: { color: axisColor } },
        grid: { left: 36, right: 12, top: 36, bottom: 24 },
        xAxis: {
          type: "category",
          data: points.map((p) => p.date.slice(5)),
          axisLabel: { color: axisColor },
          axisLine: { lineStyle: { color: "rgba(148,163,184,0.2)" } },
        },
        yAxis: {
          type: "value",
          minInterval: 1,
          axisLabel: { color: axisColor },
          splitLine: { lineStyle: { color: "rgba(148,163,184,0.12)" } },
        },
        series: [
          {
            name: "高风险",
            type: "line",
            smooth: true,
            data: points.map((p) => p.highCount),
            itemStyle: { color: "#f87171" },
            areaStyle: { opacity: 0.08 },
          },
          {
            name: "需关注",
            type: "line",
            smooth: true,
            data: points.map((p) => p.mediumCount),
            itemStyle: { color: "#fbbf24" },
            areaStyle: { opacity: 0.08 },
          },
          {
            name: "正常",
            type: "line",
            smooth: true,
            data: points.map((p) => p.lowCount),
            itemStyle: { color: CHART_COLORS[3] },
            areaStyle: { opacity: 0.08 },
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

  if (!authed) return null;

  return (
    <section aria-label="风险趋势" className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-content">风险趋势</h2>
        <div className="flex items-center gap-2 text-xs">
          {PERIODS.map((period) => (
            <button
              key={period}
              type="button"
              onClick={() => setDays(period)}
              className={`rounded-lg px-2.5 py-1 transition-all duration-200 ${
                days === period ? "bg-primary/15 text-primary" : "text-secondary hover:bg-card-hover"
              }`}
            >
              {period} 天
            </button>
          ))}
        </div>
      </div>
      {state.phase === "loading" && <div className="skeleton h-56 rounded-xl" />}
      {state.phase === "error" && <p className="text-sm text-danger">{state.message}</p>}
      {state.phase === "empty" && (
        <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
          暂无趋势数据：点「立即扫描」或等待每日快照后，这里会展示风险等级分布走势
        </p>
      )}
      {state.phase === "ready" && (
        <div ref={containerRef} className="h-56 rounded-xl border border-secondary/10 bg-card" role="img" aria-label="风险趋势图" />
      )}
    </section>
  );
}

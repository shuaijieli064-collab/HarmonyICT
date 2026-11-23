"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { EChartsType } from "echarts";
import { listEntities, listFacts } from "@/services/world";
import { simulateDecision, type SimulationResponse, type SimulationScenario } from "@/services/insight";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { CHART_BASE, CHART_COLORS, RISK_LEVEL_MAP } from "@/lib/constants";
import type { Entity, RiskLevel } from "@/types";

/** 假设推演（P1）：订阅类实体 12 个月沙盘投影，只读不落库 */
export default function SimulationPanel() {
  const authed = useIsLoggedIn();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<EChartsType | null>(null);
  const [candidates, setCandidates] = useState<Entity[] | null>(null);
  const [selectedId, setSelectedId] = useState<string>("");
  const [scenario, setScenario] = useState<SimulationScenario>("cancel");
  const [result, setResult] = useState<SimulationResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** 可推演实体 = 拥有 subscription_price 事实的实体 */
  const loadCandidates = useCallback(async (): Promise<Entity[]> => {
    const [entityPage, factPage] = await Promise.all([
      listEntities({ page: 1, pageSize: 100 }),
      listFacts({ predicate: "subscription_price", page: 1, pageSize: 100 }),
    ]);
    const withPrice = new Set(factPage.records.map((fact) => fact.subjectId));
    return entityPage.records.filter((entity) => withPrice.has(entity.id));
  }, []);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    loadCandidates()
      .then((records) => {
        if (!cancelled) {
          setCandidates(records);
          if (records.length > 0) setSelectedId(records[0].id);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "订阅实体加载失败，请确认后端 8080 已启动");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, loadCandidates]);

  /** 推演结果变化时渲染 12 个月投影曲线（动态 import 避免 SSR 引用 window） */
  useEffect(() => {
    if (!result || !containerRef.current) return;
    let disposed = false;
    let resizeHandler: (() => void) | null = null;
    void (async () => {
      const echarts = await import("echarts");
      if (disposed || !containerRef.current) return;
      const chart = echarts.init(containerRef.current);
      chartRef.current = chart;
      const cumulativeName = result.scenario === "cancel" ? "累计节省" : "累计支出";
      chart.setOption({
        color: [...CHART_COLORS],
        tooltip: { trigger: "axis" },
        legend: { textStyle: { color: "#94A3B8" } },
        grid: { left: 48, right: 16, top: 36, bottom: 28 },
        xAxis: {
          type: "category",
          data: result.months.map((month) => month.label),
          ...CHART_BASE,
        },
        yAxis: { type: "value", ...CHART_BASE },
        series: [
          { name: "当月支出", type: "line", data: result.months.map((m) => m.spend) },
          { name: cumulativeName, type: "line", smooth: true, data: result.months.map((m) => m.cumulative) },
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
  }, [result]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后使用假设推演</p>
        <Link href="/login" className="inline-block text-sm text-primary hover:underline">
          前往登录 / 注册 →
        </Link>
      </div>
    );
  }

  const handleSimulate = async () => {
    if (!selectedId) return;
    setBusy(true);
    setError(null);
    try {
      setResult(await simulateDecision({ entityId: selectedId, scenario }));
    } catch (err) {
      setResult(null);
      setError(err instanceof Error ? err.message : "推演请求失败");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-secondary/10 bg-card p-4">
        <label className="text-xs text-muted" htmlFor="simulation-entity">
          订阅实体
        </label>
        <select
          id="simulation-entity"
          value={selectedId}
          onChange={(event) => setSelectedId(event.target.value)}
          className="rounded-lg border border-secondary/20 bg-card px-3 py-1.5 text-sm text-content"
        >
          {candidates === null && <option>加载中…</option>}
          {candidates !== null && candidates.length === 0 && <option value="">无可推演订阅</option>}
          {(candidates ?? []).map((entity) => (
            <option key={entity.id} value={entity.id}>
              {entity.name}
            </option>
          ))}
        </select>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setScenario("cancel")}
            className={`rounded-lg border px-3 py-1.5 text-xs transition-colors ${
              scenario === "cancel"
                ? "border-success/40 bg-success/15 text-success"
                : "border-secondary/10 text-secondary hover:text-content"
            }`}
          >
            退订推演
          </button>
          <button
            type="button"
            onClick={() => setScenario("keep")}
            className={`rounded-lg border px-3 py-1.5 text-xs transition-colors ${
              scenario === "keep"
                ? "border-warning/40 bg-warning/15 text-warning"
                : "border-secondary/10 text-secondary hover:text-content"
            }`}
          >
            保留推演
          </button>
        </div>
        <button
          type="button"
          onClick={() => void handleSimulate()}
          disabled={busy || !selectedId}
          className="rounded-lg bg-primary px-4 py-1.5 text-xs font-semibold text-primary-foreground disabled:opacity-60"
        >
          {busy ? "推演中…" : "开始推演"}
        </button>
        <span className="text-xs text-muted">只读沙盘，不产生真实风险</span>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {candidates !== null && candidates.length === 0 && (
        <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
          暂无可推演的订阅实体：需同时存在实体与 subscription_price 事实
        </p>
      )}

      {result && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-baseline gap-x-6 gap-y-2 rounded-xl border border-secondary/10 bg-card p-4">
            <div>
              <p className="text-xs text-muted">月费用</p>
              <p className="text-xl font-semibold text-content">{result.monthlyCost}</p>
            </div>
            <div>
              <p className="text-xs text-muted">
                12 个月{result.scenario === "cancel" ? "累计节省" : "累计支出"}
              </p>
              <p className="text-xl font-semibold text-content">
                {result.months[result.months.length - 1]?.cumulative}
              </p>
            </div>
            <p className="w-full text-sm text-primary">{result.recommendation}</p>
          </div>

          <div
            ref={containerRef}
            className="h-64 w-full rounded-xl border border-secondary/10 bg-card p-2"
          />

          {(result.resolvedRisks.length > 0 || result.introducedRisks.length > 0) && (
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2 rounded-xl border border-secondary/10 bg-card p-4">
                <h3 className="text-sm font-medium text-success">消除的风险</h3>
                {result.resolvedRisks.length === 0 ? (
                  <p className="text-xs text-muted">无</p>
                ) : (
                  result.resolvedRisks.map((risk) => (
                    <p key={risk.ruleCode} className="text-xs text-secondary">
                      <span className={`${RISK_LEVEL_MAP[risk.level as RiskLevel].text} ${RISK_LEVEL_MAP[risk.level as RiskLevel].bg}`}>
                        [{RISK_LEVEL_MAP[risk.level as RiskLevel].label}]
                      </span>{" "}
                      {risk.title}
                    </p>
                  ))
                )}
              </div>
              <div className="space-y-2 rounded-xl border border-secondary/10 bg-card p-4">
                <h3 className="text-sm font-medium text-warning">新引入的假设风险</h3>
                {result.introducedRisks.length === 0 ? (
                  <p className="text-xs text-muted">无</p>
                ) : (
                  result.introducedRisks.map((risk) => (
                    <p key={risk.ruleCode} className="text-xs text-secondary">
                      <span className={`${RISK_LEVEL_MAP[risk.level as RiskLevel].text} ${RISK_LEVEL_MAP[risk.level as RiskLevel].bg}`}>
                        [{RISK_LEVEL_MAP[risk.level as RiskLevel].label}]
                      </span>{" "}
                      {risk.title}
                    </p>
                  ))
                )}
              </div>
            </div>
          )}

          <details className="rounded-xl border border-secondary/10 bg-card p-4">
            <summary className="cursor-pointer text-xs text-secondary">推演口径与假设</summary>
            <ul className="mt-2 space-y-1">
              {result.rationale.map((line) => (
                <li key={line} className="text-xs leading-relaxed text-muted">
                  · {line}
                </li>
              ))}
            </ul>
          </details>
        </div>
      )}
    </div>
  );
}

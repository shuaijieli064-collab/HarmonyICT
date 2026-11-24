"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ListPlus, LoaderCircle, Radar } from "lucide-react";
import RiskSummaryCard from "@/components/dashboard/risk-summary-card";
import { acknowledgeRisk, getRiskDetail, listRisks, resolveRisk, triggerRiskScan } from "@/services/risks";
import { createTask } from "@/services/tasks";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { RISK_LEVEL_MAP } from "@/lib/constants";
import type { Fact, Risk, RiskItem, RiskLevel } from "@/types";

/** 情报中心风险列表（已切真实接口）：列表 + 详情展开（relatedFacts 风险依据）+
 * 确认/解决 + 一键派生待办（riskId 关联）+ 手动触发扫描（演示免等 60s）。
 */
export default function RiskList() {
  const authed = useIsLoggedIn();
  const [risks, setRisks] = useState<Risk[] | null>(null);
  const [factsById, setFactsById] = useState<Record<string, Fact[]>>({});
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [scanMessage, setScanMessage] = useState<string | null>(null);
  const [derivedIds, setDerivedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  /** 纯数据拉取（不含 setState），供事件处理器复用 */
  const fetchRisks = useCallback(async (): Promise<Risk[]> => {
    const page = await listRisks({ status: "open", page: 1, pageSize: 50 });
    return page.records;
  }, []);

  /** 初始加载：setState 位于异步回调（react-hooks/set-state-in-effect） */
  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchRisks()
      .then((records) => {
        if (!cancelled) setRisks(records);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "风险列表加载失败，请确认后端 8080 已启动");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchRisks]);

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

  const toggleExpand = (riskId: string) => {
    setError(null);
    if (expandedId === riskId) {
      setExpandedId(null);
      return;
    }
    setExpandedId(riskId);
    getRiskDetail(riskId)
      .then((detail) => {
        setFactsById((prev) => ({ ...prev, [riskId]: detail.relatedFacts }));
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "风险详情加载失败");
      });
  };

  const handleAction = async (riskId: string, action: "acknowledge" | "resolve") => {
    setBusy(true);
    setError(null);
    try {
      if (action === "acknowledge") await acknowledgeRisk(riskId);
      else await resolveRisk(riskId);
      setRisks(await fetchRisks());
    } catch (err) {
      setError(err instanceof Error ? err.message : "风险状态更新失败");
    } finally {
      setBusy(false);
    }
  };

  /** 一键派生待办：风险 → 行动闭环（riskId 关联，sourceType=risk） */
  const handleDeriveTask = async (risk: Risk) => {
    setBusy(true);
    setError(null);
    try {
      await createTask({
        title: `处理：${risk.title}`,
        description: risk.description ?? (risk.ruleCode ? `触发规则 ${risk.ruleCode}` : undefined),
        priority: risk.level === "high" ? 2 : risk.level === "medium" ? 1 : 0,
        sourceType: "risk",
        riskId: risk.id,
      });
      setDerivedIds((prev) => new Set(prev).add(risk.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "待办创建失败");
    } finally {
      setBusy(false);
    }
  };

  /** 手动触发扫描（演示免等 60s 定时周期） */
  const handleScan = async () => {
    setScanning(true);
    setScanMessage(null);
    setError(null);
    try {
      const { created } = await triggerRiskScan();
      setScanMessage(created > 0 ? `本轮新增 ${created} 条风险` : "本轮无新增风险（数据未变化）");
      setRisks(await fetchRisks());
    } catch (err) {
      setError(err instanceof Error ? err.message : "扫描触发失败");
    } finally {
      setScanning(false);
    }
  };

  if (risks === null) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 2 }).map((_, index) => (
          <div key={index} className="skeleton h-28 rounded-xl" />
        ))}
      </div>
    );
  }

  const toSummary = (risk: Risk): RiskItem => ({
    id: risk.id,
    level: risk.level,
    title: risk.title,
    reason: risk.description || (risk.ruleCode ? `触发规则 ${risk.ruleCode}` : risk.riskType),
    ruleCode: risk.ruleCode,
    relatedFactCount: null,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        {error ? (
          <p className="text-sm text-danger">{error}</p>
        ) : scanMessage ? (
          <p className="text-sm text-success">{scanMessage}</p>
        ) : (
          <span />
        )}
        <button
          type="button"
          onClick={() => void handleScan()}
          disabled={scanning}
          className="flex shrink-0 items-center gap-1.5 rounded-lg border border-secondary/20 px-3 py-1.5 text-xs text-secondary transition-all duration-200 hover:border-primary/40 hover:text-primary active:scale-[0.97] disabled:opacity-50"
        >
          {scanning ? (
            <LoaderCircle size={13} strokeWidth={2} className="animate-spin" aria-hidden="true" />
          ) : (
            <Radar size={13} strokeWidth={1.75} aria-hidden="true" />
          )}
          {scanning ? "扫描中…" : "立即扫描"}
        </button>
      </div>
      {risks.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-secondary/10 bg-card p-6 text-center">
          <Radar size={22} strokeWidth={1.5} className="text-muted" aria-hidden="true" />
          <p className="text-sm text-secondary">暂无 open 风险</p>
          <p className="text-xs text-muted">上传资料完成抽取入库后，点「立即扫描」即可产出风险</p>
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {risks.map((risk) => (
            <div key={risk.id} className="space-y-2">
              <RiskSummaryCard risk={toSummary(risk)} />
              <div className="flex flex-wrap items-center gap-2 px-1">
                <button
                  type="button"
                  onClick={() => toggleExpand(risk.id)}
                  className="text-xs text-primary transition-colors hover:underline"
                >
                  {expandedId === risk.id ? "收起依据" : "查看风险依据"}
                </button>
                <button
                  type="button"
                  onClick={() => void handleDeriveTask(risk)}
                  disabled={busy || derivedIds.has(risk.id)}
                  className="flex items-center gap-1 rounded-lg border border-primary/40 px-2.5 py-1 text-xs text-primary transition-all duration-200 active:scale-[0.97] disabled:opacity-60"
                >
                  <ListPlus size={12} strokeWidth={2} aria-hidden="true" />
                  {derivedIds.has(risk.id) ? "已创建待办" : "生成待办"}
                </button>
                <button
                  type="button"
                  onClick={() => void handleAction(risk.id, "acknowledge")}
                  disabled={busy}
                  className="rounded-lg border border-warning/40 px-2.5 py-1 text-xs text-warning transition-all duration-200 active:scale-[0.97] disabled:opacity-60"
                >
                  确认
                </button>
                <button
                  type="button"
                  onClick={() => void handleAction(risk.id, "resolve")}
                  disabled={busy}
                  className="rounded-lg border border-success/40 px-2.5 py-1 text-xs text-success transition-all duration-200 active:scale-[0.97] disabled:opacity-60"
                >
                  解决
                </button>
                <span className="ml-auto text-xs text-muted">
                  {RISK_LEVEL_MAP[risk.level as RiskLevel].label}
                </span>
              </div>
              {expandedId === risk.id && (
                <div className="rounded-lg border border-secondary/10 bg-card p-3">
                  {(factsById[risk.id] ?? []).length === 0 ? (
                    <p className="text-xs text-muted">{factsById[risk.id] ? "无关联事实" : "正在加载事实快照…"}</p>
                  ) : (
                    <ul className="space-y-1.5">
                      {factsById[risk.id].map((fact) => (
                        <li key={fact.id} className="text-xs leading-relaxed text-secondary">
                          <span className="text-content">{fact.predicate}</span>
                          ：{fact.objectValue}
                          {fact.unit ? ` ${fact.unit}` : ""} · 置信度 {fact.confidence}
                          {fact.evidence ? ` · ${fact.evidence}` : ""}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
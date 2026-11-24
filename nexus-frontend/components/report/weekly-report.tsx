"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { RISK_LEVEL_MAP } from "@/lib/constants";
import { getWeeklyReport } from "@/services/reports";
import { isLoggedIn } from "@/services/auth";
import type { WeeklyReport } from "@/types";

/** 周报数据区（体量扩充第二波）：报告期概览 + 每日趋势条形 + 代表性风险。
 * 7/14/30 天可切换；趋势用 CSS 条形呈现，避免引入额外图表依赖。
 */

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "ready"; report: WeeklyReport };

const PERIODS = [7, 14, 30] as const;

function MetricCell({ label, value, hint }: { label: string; value: string | number; hint: string }) {
  return (
    <div className="rounded-xl border border-secondary/10 bg-card p-4">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-2xl font-bold text-content">{value}</p>
      <p className="mt-1 text-xs text-secondary">{hint}</p>
    </div>
  );
}

export default function WeeklyReportPanel() {
  const [days, setDays] = useState<number>(7);
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  useEffect(() => {
    let cancelled = false;
    const load = async (): Promise<LoadState> => {
      if (!isLoggedIn()) {
        return { phase: "error", message: "请先登录后再查看报告" };
      }
      try {
        return { phase: "ready", report: await getWeeklyReport(days) };
      } catch (err) {
        return {
          phase: "error",
          message: err instanceof Error ? err.message : "报告加载失败，请确认后端 8080 已启动",
        };
      }
    };
    load()
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) setState({ phase: "error", message: err instanceof Error ? err.message : "报告加载失败" });
      });
    return () => {
      cancelled = true;
    };
  }, [days]);

  if (state.phase !== "ready") {
    return (
      <section
        aria-label="周期报告"
        className="flex min-h-40 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        {state.phase === "loading" ? (
          <p className="text-sm text-muted">正在生成报告…</p>
        ) : (
          <div className="space-y-3 text-center">
            <p className="text-sm text-warning">{state.message}</p>
            <Link href="/login" className="inline-block text-sm text-primary hover:underline">
              前往登录 / 注册 →
            </Link>
          </div>
        )}
      </section>
    );
  }

  const report = state.report;
  const maxDaily = Math.max(1, ...report.dailyTrend.map((t) => t.facts + t.events));

  return (
    <section aria-label="周期报告" className="space-y-6">
      <div className="flex items-center gap-2 text-sm">
        {PERIODS.map((period) => (
          <button
            key={period}
            type="button"
            onClick={() => setDays(period)}
            className={`rounded-lg px-3 py-1.5 transition-colors ${
              days === period ? "bg-primary/15 text-primary" : "text-secondary hover:bg-card-hover"
            }`}
          >
            近 {period} 天
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        <MetricCell label="新增事实" value={report.newFactCount} hint="AI 抽取沉淀" />
        <MetricCell label="新增事件" value={report.newEventCount} hint="时间线动态" />
        <MetricCell label="新增风险" value={report.newRiskCount} hint="规则引擎发现" />
        <MetricCell label="完成任务" value={report.taskCompletedCount} hint="含 Agent 派生" />
        <MetricCell label="Agent 执行" value={report.agentRunCount} hint="编排次数" />
        <MetricCell label="通知" value={report.notificationCount} hint="站内提醒" />
      </div>

      <div className="rounded-xl border border-secondary/10 bg-card p-4">
        <h3 className="mb-3 text-sm font-semibold text-content">每日世界模型活跃度（事实 / 事件）</h3>
        <div className="flex h-32 items-end gap-1.5">
          {report.dailyTrend.map((trend) => {
            const total = trend.facts + trend.events;
            return (
              <div key={trend.day} className="flex min-w-0 flex-1 flex-col items-center gap-1">
                <div
                  className="w-full rounded-t bg-primary/60"
                  style={{ height: `${(total / maxDaily) * 100}%` }}
                  title={`${trend.day}：事实 ${trend.facts} · 事件 ${trend.events}`}
                />
                <span className="w-full truncate text-center text-[10px] text-muted">{trend.day.slice(5)}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="rounded-xl border border-secondary/10 bg-card p-4">
        <h3 className="mb-3 text-sm font-semibold text-content">期内代表性风险</h3>
        {report.notableRisks.length === 0 ? (
          <p className="text-sm text-muted">报告期内没有未解决的显著风险</p>
        ) : (
          <ul className="space-y-2">
            {report.notableRisks.map((risk) => {
              const style = RISK_LEVEL_MAP[risk.level as keyof typeof RISK_LEVEL_MAP];
              return (
                <li key={risk.id} className="flex items-center gap-2 text-sm">
                  <span className={`rounded px-2 py-0.5 text-xs ${style?.bg ?? "bg-secondary/15"} ${style?.text ?? "text-secondary"}`}>
                    {style?.label ?? risk.level}
                  </span>
                  <span className="text-content">{risk.title}</span>
                  {risk.ruleCode && <span className="text-xs text-muted">{risk.ruleCode}</span>}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}

"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import StatusCard from "@/components/dashboard/status-card";
import EventTimeline from "@/components/dashboard/event-timeline";
import RiskSummaryCard from "@/components/dashboard/risk-summary-card";
import AgentTaskCard from "@/components/dashboard/agent-task-card";
import GoalProgressCard from "@/components/dashboard/goal-progress";
import TodayActions from "@/components/dashboard/today-actions";
import { listEvents } from "@/services/world";
import { listRisks } from "@/services/risks";
import { listRuns } from "@/services/agent";
import { getDashboardSummary } from "@/services/dashboard";
import { isLoggedIn } from "@/services/auth";
import { USE_MOCK_FALLBACK } from "@/lib/constants";
import { MOCK_ACTIONS, MOCK_GOALS } from "@/mock/dashboard";
import type {
  AgentRun,
  AgentTask,
  EventItem,
  Risk,
  RiskItem,
  StatusMetric,
  WorldEvent,
} from "@/types";

/** 驾驶舱数据区（已切真实接口）：状态指标、近期事件、风险摘要、Agent 任务。
 * 目标进度与今日行动契约暂无对应接口，沿用 Mock（mock/dashboard.ts 已标注）。
 */

function toStatusMetric(metrics: {
  factTotal: number;
  entityTotal: number;
  openRiskTotal: number;
  taskTotal: number;
}): StatusMetric[] {
  return [
    { id: "facts", label: "已沉淀事实", value: String(metrics.factTotal), hint: "AI 抽取入库" },
    { id: "entities", label: "世界实体", value: String(metrics.entityTotal), hint: "人 / 资产 / 事件等" },
    { id: "risks", label: "待处理风险", value: String(metrics.openRiskTotal), hint: "状态 open" },
    { id: "tasks", label: "任务总数", value: String(metrics.taskTotal), hint: "手动 / 风险 / Agent" },
  ];
}

/** 事件条目 → 时间线展示（世界模型事件统一按 fact 类别着色） */
function toTimelineEvent(event: EventItem): WorldEvent {
  return {
    id: event.id,
    title: event.title,
    description: event.description || event.eventType,
    occurredAt: event.occurredAt.replace("T", " ").slice(0, 16),
    kind: "fact",
  };
}

/** 风险条目 → 摘要卡（关联事实数需详情接口，摘要层置 null 隐藏） */
function toRiskItem(risk: Risk): RiskItem {
  return {
    id: risk.id,
    level: risk.level,
    title: risk.title,
    reason: risk.description || (risk.ruleCode ? `触发规则 ${risk.ruleCode}` : risk.riskType),
    ruleCode: risk.ruleCode,
    relatedFactCount: null,
  };
}

/** run 状态 → Agent 任务卡状态（契约 8 后端枚举 running/succeeded/failed/cancelled） */
function toAgentTask(run: AgentRun): AgentTask {
  const status: AgentTask["status"] =
    run.status === "running" ? "running" : run.status === "succeeded" ? "done" : "failed";
  return {
    id: run.id,
    title: run.intent,
    status,
    updatedAt: (run.finishedAt ?? run.startedAt ?? "").replace("T", " ").slice(0, 16),
  };
}

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | {
      phase: "ready";
      metrics: StatusMetric[];
      events: WorldEvent[];
      risks: RiskItem[];
      runs: AgentTask[];
    };

export default function DashboardData() {
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  /** 数据加载：setState 全部位于异步回调（react-hooks/set-state-in-effect） */
  useEffect(() => {
    let cancelled = false;
    const load = (async (): Promise<LoadState> => {
      if (!isLoggedIn()) {
        return { phase: "error", message: "请先登录后再查看驾驶舱数据" };
      }
      try {
        // 概览指标走聚合接口（一次返回计数与风险分布），事件/风险/Agent 列表仍取各自契约接口
        const [summary, risks, events, runs] = await Promise.all([
          getDashboardSummary(),
          listRisks({ status: "open", page: 1, pageSize: 5 }),
          listEvents({ page: 1, pageSize: 5 }),
          listRuns({ page: 1, pageSize: 3 }),
        ]);
        return {
          phase: "ready",
          metrics: toStatusMetric({
            factTotal: summary.factTotal,
            entityTotal: summary.entityTotal,
            openRiskTotal: summary.openRiskTotal,
            taskTotal: summary.taskTotal,
          }),
          events: events.records.map(toTimelineEvent),
          risks: risks.records.map(toRiskItem),
          runs: runs.records.map(toAgentTask),
        };
      } catch (err) {
        return {
          phase: "error",
          message: err instanceof Error ? err.message : "数据加载失败，请确认后端 8080 已启动",
        };
      }
    })();
    load
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({
            phase: "error",
            message: err instanceof Error ? err.message : "数据加载失败，请确认后端 8080 已启动",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.phase !== "ready") {
    return (
      <section
        aria-label="驾驶舱数据"
        className="flex min-h-40 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        {state.phase === "loading" ? (
          <p className="text-sm text-muted">正在加载真实接口数据…</p>
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

  return (
    <>
      <section aria-label="当前状态">
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {state.metrics.map((metric) => (
            <StatusCard key={metric.id} metric={metric} />
          ))}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-label="近期事件">
          <h2 className="mb-3 text-base font-semibold text-content">近期事件</h2>
          {state.events.length === 0 ? (
            <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
              暂无事件：上传资料并完成抽取后，这里会展示世界模型的最新动态
            </p>
          ) : (
            <EventTimeline events={state.events} />
          )}
        </section>

        <div className="space-y-6">
          <section aria-label="风险摘要">
            <h2 className="mb-3 text-base font-semibold text-content">风险摘要</h2>
            {state.risks.length === 0 ? (
              <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
                暂无待处理风险
              </p>
            ) : (
              <div className="space-y-3">
                {state.risks.map((risk) => (
                  <RiskSummaryCard key={risk.id} risk={risk} />
                ))}
              </div>
            )}
          </section>

          <section aria-label="Agent 任务">
            <h2 className="mb-3 text-base font-semibold text-content">Agent 任务</h2>
            {state.runs.length === 0 ? (
              <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
                暂无执行记录，可在 Agent 中心发起
              </p>
            ) : (
              <div className="space-y-3">
                {state.runs.map((run) => (
                  <AgentTaskCard key={run.id} task={run} />
                ))}
              </div>
            )}
          </section>
        </div>
      </div>

      <section aria-label="目标进度">
        <h2 className="mb-3 text-base font-semibold text-content">目标进度</h2>
        {USE_MOCK_FALLBACK ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {MOCK_GOALS.map((goal) => (
              <GoalProgressCard key={goal.id} goal={goal} />
            ))}
          </div>
        ) : (
          <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
            目标进度为演示数据（契约暂无对应接口），设 NEXT_PUBLIC_USE_MOCK_DATA=true 开启
          </p>
        )}
      </section>

      <section aria-label="今日行动">
        <h2 className="mb-3 text-base font-semibold text-content">今日行动</h2>
        {USE_MOCK_FALLBACK ? (
          <TodayActions actions={MOCK_ACTIONS} />
        ) : (
          <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
            今日行动为演示数据（契约暂无对应接口），设 NEXT_PUBLIC_USE_MOCK_DATA=true 开启
          </p>
        )}
      </section>
    </>
  );
}
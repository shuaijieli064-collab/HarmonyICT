"use client";

import { useCallback, useEffect, useState } from "react";
import { listRunLogs } from "@/services/agent";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { AgentActionLog, AgentAuditTrail, AgentLogLevel, AgentLogResult } from "@/types";

/** Agent 执行日志审计时间线（V3 跨界接线，第②线 → 第③线）。
 * 数据源：GET /api/v1/agent/runs/{id}/logs，字段以 v3/agent 的 AgentAuditTrail 为准。
 * 只读展示：本组件不触发执行、不授权，L2 授权仍由 runs-panel 负责。
 * 边界：模型只解释、规则才下结论；时间线仅呈现已落库的审计事实，不产生任何风险判断。
 */

const LEVEL_COLOR: Record<AgentLogLevel, string> = {
  L0: "bg-primary/15 text-primary",
  L1: "bg-accent/15 text-primary",
  L2: "bg-warning/15 text-warning",
};

const RESULT_TEXT: Record<AgentLogResult, string> = {
  pending: "待执行",
  success: "执行成功",
  failure: "执行失败",
  denied: "已拒绝",
};

const RESULT_COLOR: Record<AgentLogResult, string> = {
  pending: "text-warning",
  success: "text-success",
  failure: "text-danger",
  denied: "text-danger",
};

function formatTime(value: string | null): string {
  return value ? value.replace("T", " ").slice(0, 19) : "-";
}

/** 审计摘要行：仅渲染后端真实返回的字段，值为空则不渲染该行 */
function AuditRows({ audit }: { audit: AgentAuditTrail }) {
  const rows: Array<{ label: string; value: string | null; mono?: boolean }> = [
    { label: "授权人", value: audit.authorizedBy },
    { label: "授权时间", value: audit.authorizedAt ? formatTime(audit.authorizedAt) : null },
    { label: "授权决定", value: audit.decision },
    { label: "变更字段", value: audit.changedField, mono: true },
    { label: "变更前", value: audit.beforeValue, mono: true },
    { label: "变更后", value: audit.afterValue, mono: true },
    { label: "执行摘要", value: audit.executionSummary },
  ];
  const visible = rows.filter((row) => row.value !== null && row.value !== "");
  if (visible.length === 0) return null;
  return (
    <dl className="mt-3 space-y-1.5 border-t border-secondary/10 pt-3">
      {visible.map((row) => (
        <div key={row.label} className="flex flex-wrap gap-x-2 text-xs">
          <dt className="w-16 shrink-0 text-muted">{row.label}</dt>
          <dd className={`min-w-0 flex-1 break-all text-secondary ${row.mono ? "font-mono" : ""}`}>
            {row.value}
          </dd>
        </div>
      ))}
      {audit.failureReason && (
        <div className="flex flex-wrap gap-x-2 text-xs">
          <dt className="w-16 shrink-0 text-muted">失败原因</dt>
          <dd className="min-w-0 flex-1 break-all text-danger">{audit.failureReason}</dd>
        </div>
      )}
    </dl>
  );
}

interface AuditTimelineProps {
  runId: string;
  /** 外部数据版本号：授权/刷新后 +1，触发时间线重新拉取 */
  refreshKey?: number;
}

export default function AuditTimeline({ runId, refreshKey = 0 }: AuditTimelineProps) {
  const authed = useIsLoggedIn();
  const [logs, setLogs] = useState<AgentActionLog[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchLogs = useCallback(async (): Promise<AgentActionLog[]> => {
    const page = await listRunLogs(runId, { page: 1, pageSize: 100 });
    return page.records;
  }, [runId]);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchLogs()
      .then((records) => {
        if (!cancelled) {
          setLogs(records);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "审计日志加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchLogs, refreshKey]);

  if (!authed) return <p className="text-sm text-warning">请先登录查看审计日志</p>;
  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (logs === null) return <p className="text-sm text-muted">正在加载审计日志…</p>;
  if (logs.length === 0) {
    return <p className="text-sm text-muted">该执行暂无操作日志，触发编排后审计记录会在此呈现</p>;
  }

  return (
    <ol className="relative space-y-4 border-l border-secondary/20 pl-5">
      {logs.map((log) => {
        const audit = log.audit;
        return (
          <li key={log.id} className="relative">
            <span
              className={`absolute -left-[26px] top-1.5 h-2.5 w-2.5 rounded-full ring-4 ring-card ${
                log.result === "failure" || log.result === "denied"
                  ? "bg-danger"
                  : log.result === "pending"
                    ? "bg-warning"
                    : "bg-success"
              }`}
              aria-hidden="true"
            />
            <div className="rounded-xl border border-secondary/10 bg-base p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-semibold text-content">
                  <span className={`mr-2 rounded-full px-2 py-0.5 text-xs ${LEVEL_COLOR[log.level]}`}>
                    {log.level}
                  </span>
                  {audit?.action ?? log.actionType}
                </p>
                <span className={`text-xs ${RESULT_COLOR[log.result]}`}>{RESULT_TEXT[log.result]}</span>
              </div>
              <p className="mt-1 text-xs text-muted">
                {formatTime(log.createdAt)}
                {log.targetType && log.targetId ? ` · ${log.targetType} #${log.targetId}` : ""}
                {log.authorized ? " · 已授权" : ""}
              </p>
              {audit?.executionSummary && (
                <p className="mt-2 text-xs leading-relaxed text-secondary">{audit.executionSummary}</p>
              )}
              {audit && <AuditRows audit={audit} />}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

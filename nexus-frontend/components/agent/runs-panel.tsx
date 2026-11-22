"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { createRun, executeRun, authorizeRun, getRunDetail, listRuns } from "@/services/agent";
import { listTasks } from "@/services/tasks";
import { useIsLoggedIn } from "@/hooks/use-auth";
import { FACT_STATUS_MAP } from "@/lib/constants";
import type { AgentActionLog, AgentRun, Fact, RunDetail } from "@/types";

/** Agent 中心面板（已切真实接口）：发起执行 → 编排 → L2 授权。
 * 契约 8：authorize(approve=true) 后后端立即真实执行动作（create_task / update_fact），
 * 授权完成后刷新 run 详情与任务列表（端到端演示链路最后一环）。
 */

const RUN_STATUS_TEXT: Record<string, { label: string; className: string }> = {
  running: { label: "执行中", className: "bg-primary/15 text-primary" },
  succeeded: { label: "已完成", className: "bg-success/15 text-success" },
  failed: { label: "失败", className: "bg-danger/15 text-danger" },
  cancelled: { label: "已取消", className: "bg-secondary/15 text-muted" },
};

const LOG_RESULT_TEXT: Record<AgentActionLog["result"], string> = {
  pending: "待执行",
  success: "执行成功",
  failure: "执行失败",
  denied: "已拒绝",
};

function formatPayload(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}

function formatTime(value: string | null): string {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

/** payload 中若携带新写入的事实数组则展示（create_task 场景为任务字段对象） */
function parsePayloadFacts(log: AgentActionLog): Fact[] {
  try {
    const parsed = JSON.parse(log.payload) as unknown;
    return Array.isArray(parsed) ? (parsed as Fact[]) : [];
  } catch {
    return [];
  }
}

export default function RunsPanel() {
  const authed = useIsLoggedIn();
  const [runs, setRuns] = useState<AgentRun[] | null>(null);
  const [detail, setDetail] = useState<RunDetail | null>(null);
  const [intent, setIntent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  /** 纯数据拉取（不含 setState），供事件处理器与 effect 复用 */
  const fetchRuns = useCallback(async (): Promise<AgentRun[]> => {
    const page = await listRuns({ page: 1, pageSize: 20 });
    return page.records;
  }, []);

  const fetchRunDetail = useCallback(async (runId: string): Promise<RunDetail> => {
    return getRunDetail(runId);
  }, []);

  const showError = useCallback((err: unknown, fallback: string) => {
    setError(err instanceof Error ? err.message : fallback);
  }, []);

  /** 初始加载：setState 位于异步回调（react-hooks/set-state-in-effect） */
  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchRuns()
      .then((records) => {
        if (!cancelled) setRuns(records);
      })
      .catch((err: unknown) => {
        if (!cancelled) showError(err, "执行记录加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchRuns, showError]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后使用 Agent 中心</p>
        <Link href="/login" className="inline-block text-sm text-primary hover:underline">
          前往登录 / 注册 →
        </Link>
      </div>
    );
  }

  const onCreateRun = async () => {
    const trimmed = intent.trim();
    if (!trimmed) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const run = await createRun({ intent: trimmed, triggerType: "manual" });
      setIntent("");
      const [records, nextDetail] = await Promise.all([fetchRuns(), fetchRunDetail(run.id)]);
      setRuns(records);
      setDetail(nextDetail);
      setNotice("执行记录已创建，点击「执行编排」让 Agent 开始工作");
    } catch (err) {
      showError(err, "创建执行记录失败");
    } finally {
      setBusy(false);
    }
  };

  const onExecute = async (runId: string) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await executeRun(runId);
      setDetail(await fetchRunDetail(runId));
      setNotice("编排已触发（异步执行），稍候刷新查看日志与待授权操作");
    } catch (err) {
      showError(err, "触发编排失败");
    } finally {
      setBusy(false);
    }
  };

  /** L2 授权（契约 8）：approve=true 后后端立即真实执行，须刷新详情与任务列表 */
  const onAuthorize = async (runId: string, logIds: string[], approve: boolean) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await authorizeRun(runId, { logIds, approve });
      const [nextDetail, tasks] = await Promise.all([fetchRunDetail(runId), listTasks({ page: 1, pageSize: 20 })]);
      setDetail(nextDetail);
      setNotice(
        approve
          ? `授权成功，动作已真实执行；后端任务总数 ${tasks.total}，列表已同步`
          : "已拒绝该操作"
      );
    } catch (err) {
      showError(err, "授权操作失败");
    } finally {
      setBusy(false);
    }
  };

  const pendingL2Logs =
    detail?.logs.filter((log) => log.level === "L2" && !log.authorized && log.result === "pending") ?? [];

  return (
    <div className="space-y-6">
      <section aria-label="发起执行" className="rounded-xl border border-secondary/10 bg-card p-4">
        <label htmlFor="intent" className="mb-2 block text-sm font-semibold text-content">
          发起 Agent 执行
        </label>
        <div className="flex gap-2">
          <input
            id="intent"
            value={intent}
            onChange={(e) => setIntent(e.target.value)}
            placeholder="例如：整理本月资产变动"
            className="flex-1 rounded-lg border border-secondary/20 bg-base px-3 py-2 text-sm text-content outline-none focus:border-primary"
          />
          <button
            type="button"
            onClick={() => void onCreateRun()}
            disabled={busy || !intent.trim()}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60"
          >
            创建
          </button>
        </div>
      </section>

      {error && <p className="text-sm text-danger">{error}</p>}
      {notice && <p className="text-sm text-success">{notice}</p>}

      <section aria-label="执行记录列表" className="space-y-3">
        <h2 className="text-base font-semibold text-content">执行记录</h2>
        {runs === null ? (
          <p className="text-sm text-muted">正在加载…</p>
        ) : runs.length === 0 ? (
          <p className="text-sm text-muted">暂无执行记录，先在上方发起一条</p>
        ) : (
          runs.map((run) => {
            const status = RUN_STATUS_TEXT[run.status];
            const isActive = detail?.run.id === run.id;
            return (
              <div
                key={run.id}
                className={`flex items-center justify-between gap-3 rounded-xl border p-4 ${
                  isActive ? "border-primary/40 bg-card" : "border-secondary/10 bg-card"
                }`}
              >
                <button
                  type="button"
                  onClick={() => {
                    setError(null);
                    fetchRunDetail(run.id)
                      .then(setDetail)
                      .catch((err: unknown) => showError(err, "详情加载失败"));
                  }}
                  className="min-w-0 flex-1 text-left"
                >
                  <p className="truncate text-sm font-semibold text-content">{run.intent}</p>
                  <p className="mt-1 text-xs text-muted">
                    #{run.id} · {formatTime(run.startedAt)} 开始 · {status.label}
                  </p>
                </button>
                <div className="flex shrink-0 items-center gap-2">
                  <span className={`rounded-full px-2.5 py-0.5 text-xs ${status.className}`}>{status.label}</span>
                  {run.status === "running" && (
                    <button
                      type="button"
                      onClick={() => void onExecute(run.id)}
                      disabled={busy}
                      className="rounded-lg border border-primary/40 px-3 py-1.5 text-xs text-primary hover:bg-primary/10 disabled:opacity-60"
                    >
                      执行编排
                    </button>
                  )}
                </div>
              </div>
            );
          })
        )}
      </section>

      {detail && (
        <section aria-label="执行详情" className="space-y-3 rounded-xl border border-secondary/10 bg-card p-4">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-content">
              执行详情 #{detail.run.id}
              {pendingL2Logs.length > 0 && (
                <span className="ml-2 rounded-full bg-warning/15 px-2 py-0.5 text-xs text-warning">
                  {pendingL2Logs.length} 项待授权
                </span>
              )}
            </h2>
            <button
              type="button"
              onClick={() => {
                setError(null);
                setNotice(null);
                fetchRunDetail(detail.run.id)
                  .then(setDetail)
                  .catch((err: unknown) => showError(err, "刷新失败"));
              }}
              disabled={busy}
              className="text-xs text-primary hover:underline disabled:opacity-60"
            >
              刷新
            </button>
          </div>
          {detail.run.summary && (
            <p className="rounded-lg bg-base p-3 text-sm leading-relaxed text-secondary">{detail.run.summary}</p>
          )}

          {detail.logs.length === 0 ? (
            <p className="text-sm text-muted">
              暂无操作日志：run 状态为 running 时点击「执行编排」触发 B 侧 AI 编排
            </p>
          ) : (
            detail.logs.map((log) => {
              const isAuthTarget = log.level === "L2" && !log.authorized && log.result === "pending";
              const facts = parsePayloadFacts(log);
              return (
                <div key={log.id} className="rounded-lg border border-secondary/10 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-content">
                      <span className="mr-2 rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">
                        {log.level}
                      </span>
                      {log.actionType}
                      {log.targetType && log.targetId ? ` → ${log.targetType} #${log.targetId}` : ""}
                    </p>
                    <span className="text-xs text-muted">{LOG_RESULT_TEXT[log.result]}</span>
                  </div>
                  <pre className="mt-2 overflow-x-auto rounded bg-base p-2 text-xs leading-relaxed text-secondary">
                    {formatPayload(log.payload)}
                  </pre>
                  {facts.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {facts.map((fact) => (
                        <span
                          key={fact.id}
                          className={`rounded-full px-2 py-0.5 text-xs ${
                            FACT_STATUS_MAP[fact.factStatus]?.bg ?? ""
                          } ${FACT_STATUS_MAP[fact.factStatus]?.text ?? "text-secondary"}`}
                        >
                          {fact.predicate}：{fact.objectValue}
                        </span>
                      ))}
                    </div>
                  )}
                  {isAuthTarget && (
                    <div className="mt-3 flex gap-2">
                      <button
                        type="button"
                        onClick={() => void onAuthorize(detail.run.id, [log.id], true)}
                        disabled={busy}
                        className="rounded-lg bg-success px-3 py-1.5 text-xs font-semibold text-primary-foreground disabled:opacity-60"
                      >
                        授权执行（L2）
                      </button>
                      <button
                        type="button"
                        onClick={() => void onAuthorize(detail.run.id, [log.id], false)}
                        disabled={busy}
                        className="rounded-lg border border-danger/40 px-3 py-1.5 text-xs text-danger disabled:opacity-60"
                      >
                        拒绝
                      </button>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </section>
      )}
    </div>
  );
}
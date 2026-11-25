"use client";

import { useCallback, useEffect, useState } from "react";

import { listSources } from "@/services/upload";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { Source, SourceStatus } from "@/types";

/** 来源列表面板（契约 4.3，真实接口）：展示每个上传来源的解析状态。
 * 解决「抽取超时/失败后无处查状态」：上传轮询超时的来源，回到驾驶舱在此核对
 * pending/parsing/parsed/failed，失败的可重新上传。
 */

const SOURCE_STATUS_MAP: Record<SourceStatus, { label: string; className: string }> = {
  pending: { label: "待解析", className: "bg-secondary/15 text-muted" },
  parsing: { label: "解析中", className: "bg-warning/15 text-warning" },
  parsed: { label: "已入库", className: "bg-success/15 text-success" },
  failed: { label: "抽取失败", className: "bg-danger/15 text-danger" },
};

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "ready"; sources: Source[] };

export default function SourcesPanel() {
  const authed = useIsLoggedIn();
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  const fetchSources = useCallback(async (): Promise<LoadState> => {
    const page = await listSources({ page: 1, pageSize: 50 });
    return { phase: "ready", sources: page.records };
  }, []);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchSources()
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ phase: "error", message: err instanceof Error ? err.message : "来源列表加载失败" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchSources]);

  if (!authed) {
    return (
      <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
        登录后可查看上传来源的解析状态
      </p>
    );
  }
  if (state.phase === "loading") {
    return <p className="text-sm text-muted">正在加载来源列表…</p>;
  }
  if (state.phase === "error") {
    return <p className="text-sm text-danger">{state.message}</p>;
  }
  if (state.sources.length === 0) {
    return (
      <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
        暂无上传来源：从上方上传资料后，这里会展示每个文件的抽取状态
      </p>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-secondary/10">
      <table className="w-full text-left text-sm">
        <thead className="bg-card text-xs text-secondary">
          <tr>
            <th className="px-4 py-2.5 font-medium">文件名</th>
            <th className="px-4 py-2.5 font-medium">大小</th>
            <th className="px-4 py-2.5 font-medium">解析状态</th>
            <th className="px-4 py-2.5 font-medium">来源 ID</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-secondary/10">
          {state.sources.map((source) => {
            const status = SOURCE_STATUS_MAP[source.status] ?? {
              label: source.status,
              className: "bg-secondary/15 text-muted",
            };
            return (
              <tr key={source.sourceId} className="bg-card">
                <td className="max-w-60 truncate px-4 py-2.5 text-content">{source.fileName}</td>
                <td className="px-4 py-2.5 text-xs text-secondary">{formatSize(source.fileSize)}</td>
                <td className="px-4 py-2.5">
                  <span className={`rounded-full px-2.5 py-0.5 text-xs ${status.className}`}>{status.label}</span>
                </td>
                <td className="px-4 py-2.5 text-xs text-muted">#{source.sourceId}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { NOTIFICATION_CATEGORY_MAP } from "@/lib/constants";
import { cn } from "@/lib/utils";
import { isLoggedIn } from "@/services/auth";
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/services/notifications";
import type { NotificationItem } from "@/types";

/** 通知中心数据区：全部/未读过滤、单条已读、全部已读。
 * 通知由后端定时同步产生（风险 / Agent 动作），本页只做展示与已读流转。
 */

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "ready"; items: NotificationItem[]; total: number; unreadTotal: number };

function formatTime(value: string | null): string {
  return value ? value.replace("T", " ").slice(0, 16) : "";
}

export default function NotificationList() {
  const [state, setState] = useState<LoadState>({ phase: "loading" });
  const [onlyUnread, setOnlyUnread] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async (): Promise<LoadState> => {
      if (!isLoggedIn()) {
        return { phase: "error", message: "请先登录后再查看通知" };
      }
      try {
        const [page, unread] = await Promise.all([
          listNotifications({ page: 1, pageSize: 50, unread: onlyUnread || undefined }),
          listNotifications({ page: 1, pageSize: 1, unread: true }),
        ]);
        return { phase: "ready", items: page.records, total: page.total, unreadTotal: unread.total };
      } catch (err) {
        return {
          phase: "error",
          message: err instanceof Error ? err.message : "通知加载失败，请确认后端 8080 已启动",
        };
      }
    };
    load()
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({
            phase: "error",
            message: err instanceof Error ? err.message : "通知加载失败，请确认后端 8080 已启动",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [onlyUnread]);

  const handleRead = async (id: string) => {
    try {
      await markNotificationRead(id);
    } catch {
      // 单条已读失败不打断列表（后端幂等，可重试）
    }
    setState((prev) =>
      prev.phase === "ready"
        ? {
            ...prev,
            items: prev.items.map((item) =>
              item.id === id ? { ...item, read: true, readAt: new Date().toISOString() } : item),
            unreadTotal: Math.max(0, prev.unreadTotal - 1),
          }
        : prev,
    );
  };

  const handleReadAll = async () => {
    try {
      await markAllNotificationsRead();
    } catch {
      // 同上，幂等可重试
    }
    setState((prev) =>
      prev.phase === "ready"
        ? { ...prev, items: prev.items.map((item) => ({ ...item, read: true })), unreadTotal: 0 }
        : prev,
    );
  };

  if (state.phase !== "ready") {
    return (
      <section
        aria-label="通知列表"
        className="flex min-h-40 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        {state.phase === "loading" ? (
          <p className="text-sm text-muted">正在加载通知…</p>
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
    <section aria-label="通知列表" className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3 text-sm">
          <button
            type="button"
            onClick={() => setOnlyUnread(false)}
            className={cn("rounded-lg px-3 py-1.5 transition-colors",
              !onlyUnread ? "bg-primary/15 text-primary" : "text-secondary hover:bg-card-hover")}
          >
            全部（{state.total}）
          </button>
          <button
            type="button"
            onClick={() => setOnlyUnread(true)}
            className={cn("rounded-lg px-3 py-1.5 transition-colors",
              onlyUnread ? "bg-primary/15 text-primary" : "text-secondary hover:bg-card-hover")}
          >
            未读（{state.unreadTotal}）
          </button>
        </div>
        {state.unreadTotal > 0 && (
          <button
            type="button"
            onClick={handleReadAll}
            className="text-sm text-primary hover:underline"
          >
            全部标记已读
          </button>
        )}
      </div>

      {state.items.length === 0 ? (
        <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
          暂无通知：系统会在产生新风险或 Agent 完成动作后自动提醒
        </p>
      ) : (
        <ul className="space-y-3">
          {state.items.map((item) => {
            const category = NOTIFICATION_CATEGORY_MAP[
              item.category as keyof typeof NOTIFICATION_CATEGORY_MAP
            ] ?? NOTIFICATION_CATEGORY_MAP.system;
            return (
              <li
                key={item.id}
                className={cn(
                  "rounded-xl border border-secondary/10 bg-card p-4 transition-colors",
                  !item.read && "border-primary/30",
                )}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 space-y-1.5">
                    <div className="flex items-center gap-2">
                      <span className={cn("rounded px-2 py-0.5 text-xs", category.bg, category.text)}>
                        {category.label}
                      </span>
                      <span className={cn("truncate text-sm font-medium", item.read ? "text-secondary" : "text-content")}>
                        {item.title}
                      </span>
                    </div>
                    {item.content && (
                      <p className="text-xs leading-relaxed text-secondary">{item.content}</p>
                    )}
                    <p className="text-xs text-muted">{formatTime(item.createdAt)}</p>
                  </div>
                  {!item.read && (
                    <button
                      type="button"
                      onClick={() => handleRead(item.id)}
                      className="shrink-0 text-xs text-primary hover:underline"
                    >
                      标记已读
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

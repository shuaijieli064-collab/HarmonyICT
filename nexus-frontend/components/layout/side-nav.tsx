"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Bell, LogOut, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { NAV_ITEMS } from "@/lib/constants";
import { navIconOf } from "@/lib/icons";
import { useIsLoggedIn, useUsername } from "@/hooks/use-auth";
import { clearAuth } from "@/services/auth";
import { getUnreadCount } from "@/services/notifications";
import ThemeToggle from "@/components/layout/theme-toggle";

/** 桌面端左侧导航栏（≥1024px 常驻），底部含登录态入口（useSyncExternalStore 订阅）。
 * 侧边栏与画布同色不加玻璃（避免整屏模糊的性能开销），仅激活项用玻璃面。
 * 体量扩充：每项配语义图标 + 激活项左侧主色指示条；通知项常驻未读徽标（60s 轮询）。
 */
export default function SideNav() {
  const pathname = usePathname();
  const loggedIn = useIsLoggedIn();
  const username = useUsername();
  const [unread, setUnread] = useState(0);
  // 未登录时徽标直接派生为 0（不写 state，避免 effect 内同步 setState 的级联渲染告警）
  const displayUnread = loggedIn ? unread : 0;

  /** 未读徽标轮询：60s 与后端通知同步频率对齐；未登录不发起请求 */
  useEffect(() => {
    if (!loggedIn) {
      return;
    }
    let cancelled = false;
    const poll = async () => {
      try {
        const { count } = await getUnreadCount();
        if (!cancelled) setUnread(count);
      } catch {
        // 轮询失败不打断页面（静默保持上一次值）
      }
    };
    void poll();
    const timer = setInterval(poll, 60_000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [loggedIn, pathname]);

  return (
    <aside className="hidden lg:flex w-60 shrink-0 flex-col border-r border-secondary/10 bg-transparent">
      <div className="flex h-14 items-center gap-2 px-6">
        <span className="icon-tile !h-7 !w-7 !rounded-md" aria-hidden="true">
          <Sparkles size={14} strokeWidth={2} />
        </span>
        <span className="text-gradient text-lg font-bold">NEXUS</span>
        <span className="ml-1 text-xs text-secondary">个人资料智能管家</span>
      </div>
      <nav className="flex-1 px-3 py-2">
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href;
          const Icon = navIconOf(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "relative mb-1 flex items-center gap-2.5 rounded-lg px-4 py-2.5 text-sm transition-all duration-200",
                active
                  ? "bg-primary/15 text-primary"
                  : "text-secondary hover:bg-card-hover hover:text-content",
              )}
            >
              {/* 激活项左侧主色指示条（玻璃语言的聚焦语义） */}
              {active && (
                <span
                  aria-hidden="true"
                  className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-full bg-primary"
                />
              )}
              <Icon size={16} strokeWidth={1.75} className="shrink-0" aria-hidden="true" />
              <span className="flex-1">{item.label}</span>
              {item.href === "/notifications" && displayUnread > 0 && (
                <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-danger/20 px-1.5 text-[11px] font-semibold text-danger">
                  {displayUnread > 99 ? "99+" : displayUnread}
                </span>
              )}
            </Link>
          );
        })}
      </nav>
      <div className="space-y-2 border-t border-secondary/10 px-6 py-4 text-xs">
        <div className="flex items-center justify-between">
          <span className="text-muted">主题</span>
          <ThemeToggle />
        </div>
        {loggedIn ? (
          <>
            <p className="text-secondary">
              已登录：<span className="text-content">{username ?? "用户"}</span>
            </p>
            <button
              type="button"
              onClick={() => clearAuth()}
              className="flex items-center gap-1.5 text-muted transition-colors hover:text-danger"
            >
              <LogOut size={13} strokeWidth={1.75} aria-hidden="true" />
              退出登录
            </button>
          </>
        ) : (
          <Link href="/login" className="flex items-center gap-1.5 text-primary hover:underline">
            <Bell size={13} strokeWidth={1.75} aria-hidden="true" />
            登录 / 注册（联调需先登录）
          </Link>
        )}
        <p className="text-muted">华为 ICT 创新赛 · 赛题 1</p>
      </div>
    </aside>
  );
}

"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { NAV_ITEMS } from "@/lib/constants";
import { useIsLoggedIn, useUsername } from "@/hooks/use-auth";
import { clearAuth } from "@/services/auth";

/** 桌面端左侧导航栏（≥1024px 常驻），底部含登录态入口（useSyncExternalStore 订阅） */
export default function SideNav() {
  const pathname = usePathname();
  const loggedIn = useIsLoggedIn();
  const username = useUsername();

  return (
    <aside className="hidden lg:flex w-60 shrink-0 flex-col border-r border-secondary/10 bg-card">
      <div className="flex h-14 items-center px-6">
        <span className="text-lg font-bold text-primary">NEXUS</span>
        <span className="ml-2 text-xs text-secondary">个人资料智能管家</span>
      </div>
      <nav className="flex-1 px-3 py-2">
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "mb-1 flex items-center rounded-lg px-4 py-2.5 text-sm transition-colors",
                active
                  ? "bg-primary/15 text-primary"
                  : "text-secondary hover:bg-card-hover hover:text-content"
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="space-y-2 border-t border-secondary/10 px-6 py-4 text-xs">
        {loggedIn ? (
          <>
            <p className="text-secondary">
              已登录：<span className="text-content">{username ?? "用户"}</span>
            </p>
            <button type="button" onClick={() => clearAuth()} className="text-muted hover:text-secondary">
              退出登录
            </button>
          </>
        ) : (
          <Link href="/login" className="text-primary hover:underline">
            登录 / 注册（联调需先登录）
          </Link>
        )}
        <p className="text-muted">华为 ICT 创新赛 · 赛题 1</p>
      </div>
    </aside>
  );
}
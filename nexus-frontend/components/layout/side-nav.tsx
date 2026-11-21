"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { NAV_ITEMS } from "@/lib/constants";

/** 桌面端左侧导航栏（≥1024px 常驻） */
export default function SideNav() {
  const pathname = usePathname();

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
      <div className="px-6 py-4 text-xs text-muted">华为 ICT 创新赛 · 赛题 1</div>
    </aside>
  );
}
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { NAV_ITEMS } from "@/lib/constants";

/** 移动端顶部导航（<1024px），汉堡菜单展开页面列表 */
export default function MobileNav() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const current = NAV_ITEMS.find((item) => item.href === pathname);

  return (
    <header className="lg:hidden border-b border-secondary/10 bg-card">
      <div className="flex h-14 items-center justify-between px-4">
        <Link href="/" className="text-base font-bold text-primary">
          NEXUS
        </Link>
        <span className="flex-1 pl-3 text-sm text-secondary">
          {current ? current.label : "驾驶舱"}
        </span>
        <button
          type="button"
          aria-label="打开导航菜单"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className="flex h-11 w-11 items-center justify-center rounded-lg text-secondary hover:text-content"
        >
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path
              d={open ? "M4 4l12 12M16 4L4 16" : "M2 5h16M2 10h16M2 15h16"}
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </div>
      {open && (
        <nav className="border-t border-secondary/10 px-3 py-2">
          {NAV_ITEMS.map((item) => {
            const active = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setOpen(false)}
                className={cn(
                  "mb-1 flex items-center rounded-lg px-4 py-3 text-sm",
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
      )}
    </header>
  );
}
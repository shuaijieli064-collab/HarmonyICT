"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Menu, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { NAV_ITEMS } from "@/lib/constants";
import { navIconOf } from "@/lib/icons";
import ThemeToggle from "@/components/layout/theme-toggle";

/** 移动端顶部导航（<1024px）：抽屉式菜单——遮罩 + 下滑面板（framer-motion），
 * 玻璃固定层（glass-nav）；路由切换/点遮罩自动收起。
 */
export default function MobileNav() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const current = NAV_ITEMS.find((item) => item.href === pathname);

  /** 路由变化自动收起抽屉 */
  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  /** 抽屉展开时锁定背景滚动 */
  useEffect(() => {
    document.body.style.overflow = open ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  return (
    <header className="glass-nav sticky top-0 z-40 lg:hidden">
      <div className="flex h-14 items-center justify-between px-4">
        <Link href="/" className="text-base font-bold text-primary">
          NEXUS
        </Link>
        <span className="flex-1 pl-3 text-sm text-secondary">
          {current ? current.label : "驾驶舱"}
        </span>
        <ThemeToggle />
        <button
          type="button"
          aria-label={open ? "关闭导航菜单" : "打开导航菜单"}
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className="flex h-11 w-11 items-center justify-center rounded-lg text-secondary transition-transform duration-200 active:scale-90 hover:text-content"
        >
          {open ? (
            <X size={20} strokeWidth={1.75} aria-hidden="true" />
          ) : (
            <Menu size={20} strokeWidth={1.75} aria-hidden="true" />
          )}
        </button>
      </div>

      <AnimatePresence>
        {open && (
          <>
            {/* 遮罩：点击收起 */}
            <motion.div
              key="overlay"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onClick={() => setOpen(false)}
              className="fixed inset-x-0 top-14 bottom-0 z-30 bg-overlay"
              aria-hidden="true"
            />
            {/* 抽屉面板：下滑展开 */}
            <motion.nav
              key="drawer"
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
              className="glass-nav absolute inset-x-0 top-full z-40 border-t border-secondary/10 px-3 py-2"
            >
              {NAV_ITEMS.map((item) => {
                const active = pathname === item.href;
                const Icon = navIconOf(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setOpen(false)}
                    className={cn(
                      "mb-1 flex items-center gap-2.5 rounded-lg px-4 py-3 text-sm",
                      active
                        ? "bg-primary/15 text-primary"
                        : "text-secondary hover:bg-card-hover hover:text-content"
                    )}
                  >
                    <Icon size={16} strokeWidth={1.75} className="shrink-0" aria-hidden="true" />
                    {item.label}
                  </Link>
                );
              })}
            </motion.nav>
          </>
        )}
      </AnimatePresence>
    </header>
  );
}

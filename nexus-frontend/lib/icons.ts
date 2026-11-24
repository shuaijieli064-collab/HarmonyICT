/* 全站图标映射中枢（体量：lucide-react，currentColor 跟随双主题）。
 * 图标即语义：导航按页面主功能选形，风险/通知按既有 RISK_LEVEL_MAP / NOTIFICATION_CATEGORY_MAP 一一对应。
 * 禁止在组件里散落 import 图标做同类映射，统一从这里取。
 */

import {
  Bell,
  BellRing,
  Bot,
  Boxes,
  CircleCheck,
  CircleAlert,
  Clock,
  FileBarChart,
  FlaskConical,
  Info,
  LayoutDashboard,
  Network,
  Radar,
  type LucideIcon,
} from "lucide-react";
import type { NavItem } from "@/lib/constants";

/** 导航项图标（顺序与 NAV_ITEMS 一一对应） */
export const NAV_ICON_MAP: Record<string, LucideIcon> = {
  "/": LayoutDashboard,
  "/world-model": Network,
  "/intelligence-center": Radar,
  "/decision-lab": FlaskConical,
  "/agent-center": Bot,
  "/asset-center": Boxes,
  "/notifications": Bell,
  "/reports": FileBarChart,
};

export function navIconOf(href: NavItem["href"] | string): LucideIcon {
  return NAV_ICON_MAP[href] ?? LayoutDashboard;
}

/** 风险等级图标（与 RISK_LEVEL_MAP 语义一致） */
export const RISK_LEVEL_ICON_MAP: Record<string, LucideIcon> = {
  high: CircleAlert,
  medium: Clock,
  low: CircleCheck,
};

export function riskLevelIconOf(level: string): LucideIcon {
  return RISK_LEVEL_ICON_MAP[level] ?? Info;
}

/** 通知类别图标 */
export const NOTIFICATION_CATEGORY_ICON_MAP: Record<string, LucideIcon> = {
  risk: BellRing,
  agent: Bot,
  system: Info,
};

export function notificationCategoryIconOf(category: string): LucideIcon {
  return NOTIFICATION_CATEGORY_ICON_MAP[category] ?? Info;
}

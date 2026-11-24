import type { Metadata } from "next";
import NotificationList from "@/components/notification/notification-list";

export const metadata: Metadata = { title: "通知中心 · NEXUS" };

/** 通知中心页：风险提醒与 Agent 动作结果的站内通知（后端定时同步，已读幂等） */
export default function NotificationsPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">通知中心</h1>
        <p className="mt-1 text-sm text-secondary">风险产生与 Agent 动作完成时自动提醒，重要动态不再错过</p>
      </header>
      <NotificationList />
    </div>
  );
}

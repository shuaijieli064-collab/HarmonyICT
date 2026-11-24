import type { Metadata } from "next";
import { LayoutDashboard, Upload } from "lucide-react";
import UploadPanel from "@/components/upload/upload-panel";
import DashboardData from "@/components/dashboard/dashboard-data";

export const metadata: Metadata = { title: "驾驶舱 · NEXUS" };

/** Dashboard 个人智能驾驶舱：真实接口数据区（登录后展示）+ 上传交互 */
export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <header className="flex items-center gap-2.5">
        <LayoutDashboard size={22} strokeWidth={1.75} className="text-primary" aria-hidden="true" />
        <div>
          <h1 className="text-2xl font-bold tracking-wide text-content">个人智能驾驶舱</h1>
          <p className="mt-1 text-sm text-secondary">一屏看清你的世界状态、风险与今日行动</p>
        </div>
      </header>

      <DashboardData />

      <section aria-label="资料上传">
        <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-content">
          <Upload size={16} strokeWidth={1.75} className="text-secondary" aria-hidden="true" />
          上传资料
        </h2>
        <UploadPanel />
      </section>
    </div>
  );
}

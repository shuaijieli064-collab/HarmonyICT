import type { Metadata } from "next";
import UploadPanel from "@/components/upload/upload-panel";
import SourcesPanel from "@/components/upload/sources-panel";
import DashboardData from "@/components/dashboard/dashboard-data";

export const metadata: Metadata = { title: "驾驶舱 · NEXUS" };

/** Dashboard 个人智能驾驶舱：真实接口数据区（登录后展示）+ 上传交互 */
export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">个人智能驾驶舱</h1>
        <p className="mt-1 text-sm text-secondary">一屏看清你的世界状态、风险与今日行动</p>
      </header>

      <DashboardData />

      <section aria-label="资料上传">
        <h2 className="mb-3 text-base font-semibold text-content">上传资料</h2>
        <UploadPanel />
      </section>

      <section aria-label="来源列表">
        <h2 className="mb-3 text-base font-semibold text-content">来源列表</h2>
        <SourcesPanel />
      </section>
    </div>
  );
}

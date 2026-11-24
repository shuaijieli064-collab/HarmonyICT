import type { Metadata } from "next";
import WeeklyReportPanel from "@/components/report/weekly-report";
import ExportButton from "@/components/export/export-button";

export const metadata: Metadata = { title: "周报 · NEXUS" };

/** 周报页（体量扩充第二波）：个人周期报告 + 任务 CSV 导出。 */
export default function ReportsPage() {
  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-content">个人周报</h1>
          <p className="mt-1 text-sm text-secondary">你的世界模型活跃度、风险与任务完成情况的周期回顾</p>
        </div>
        <ExportButton dataset="tasks" label="导出任务 CSV" />
      </header>
      <WeeklyReportPanel />
    </div>
  );
}

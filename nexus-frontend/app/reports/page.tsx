import type { Metadata } from "next";
import { FileBarChart } from "lucide-react";
import WeeklyReportPanel from "@/components/report/weekly-report";
import ExportButton from "@/components/export/export-button";

export const metadata: Metadata = { title: "周报 · NEXUS" };

/** 周报页（体量扩充第二波）：个人周期报告 + 任务 CSV 导出。 */
export default function ReportsPage() {
  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className="icon-tile"><FileBarChart size={20} strokeWidth={1.75} aria-hidden="true" /></span>
          <div>
            <h1 className="text-2xl font-bold tracking-wide text-content">个人周报</h1>
            <p className="mt-1 text-sm text-secondary">你的世界模型活跃度、风险与任务完成情况的周期回顾</p>
          </div>
        </div>
        <ExportButton dataset="tasks" label="导出任务 CSV" />
      </header>
      <WeeklyReportPanel />
    </div>
  );
}

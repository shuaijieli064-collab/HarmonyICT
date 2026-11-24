import type { Metadata } from "next";
import { Network } from "lucide-react";
import WorldPanel from "@/components/world/world-panel";
import EntityGraph from "@/components/world/entity-graph";
import ExportButton from "@/components/export/export-button";

export const metadata: Metadata = { title: "世界模型 · NEXUS" };

/** 世界模型页（真实接口）：实体关系图谱（ECharts 力导向，含同源/事件/风险关系边）、
 * 实体列表与创建（重名 20104 提示）、最近事实（联调核对抽取落库）、CSV 导出。
 */
export default function WorldModelPage() {
  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <Network size={22} strokeWidth={1.75} className="text-primary" aria-hidden="true" />
          <div>
            <h1 className="text-2xl font-bold tracking-wide text-content">世界模型</h1>
            <p className="mt-1 text-sm text-secondary">你的个人世界：实体、事实、事件与来源</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <ExportButton dataset="facts" label="导出事实 CSV" />
          <ExportButton dataset="entities" label="导出实体 CSV" />
          <ExportButton dataset="risks" label="导出风险 CSV" />
        </div>
      </header>
      <section aria-label="实体关系图谱" className="space-y-3">
        <h2 className="text-base font-semibold text-content">实体关系图谱</h2>
        <EntityGraph />
      </section>
      <WorldPanel />
    </div>
  );
}
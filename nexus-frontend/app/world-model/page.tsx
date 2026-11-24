import type { Metadata } from "next";
import WorldPanel from "@/components/world/world-panel";
import EntityGraph from "@/components/world/entity-graph";

export const metadata: Metadata = { title: "世界模型 · NEXUS" };

/** 世界模型页（真实接口）：实体关系图谱（ECharts 力导向）、实体列表与创建（重名 20104 提示）、
 * 最近事实（联调核对抽取落库）。
 */
export default function WorldModelPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">世界模型</h1>
        <p className="mt-1 text-sm text-secondary">你的个人世界：实体、事实、事件与来源</p>
      </header>
      <section aria-label="实体关系图谱" className="space-y-3">
        <h2 className="text-base font-semibold text-content">实体关系图谱</h2>
        <EntityGraph />
      </section>
      <WorldPanel />
    </div>
  );
}
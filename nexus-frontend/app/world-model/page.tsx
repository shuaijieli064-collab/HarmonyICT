import type { Metadata } from "next";
import WorldPanel from "@/components/world/world-panel";

export const metadata: Metadata = { title: "世界模型 · NEXUS" };

/** 世界模型页（真实接口）：实体列表与创建（重名 20104 提示）、最近事实（联调核对抽取落库）。
 * 实体关系图谱（ECharts）阶段二接入。
 */
export default function WorldModelPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">世界模型</h1>
        <p className="mt-1 text-sm text-secondary">你的个人世界：实体、事实、事件与来源</p>
      </header>
      <WorldPanel />
      <section
        aria-label="实体关系图谱"
        className="flex min-h-40 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        <p className="text-sm text-muted">实体关系图谱开发中（TODO(C, 阶段二): ECharts 关系图谱接入）</p>
      </section>
    </div>
  );
}
import type { Metadata } from "next";

export const metadata: Metadata = { title: "世界模型 · NEXUS" };

/** 世界模型页：实体关系图谱（ECharts）、事实列表、事件时间线、来源与置信度。
 * 阶段二实现图谱与时间线可视化，本轮先落页面骨架。
 */
export default function WorldModelPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">世界模型</h1>
        <p className="mt-1 text-sm text-secondary">你的个人世界：实体、事实、事件与来源</p>
      </header>
      <section
        aria-label="实体关系图谱"
        className="flex min-h-80 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        <p className="text-sm text-muted">
          实体关系图谱开发中（TODO(C, 阶段二): ECharts 关系图谱接入）
        </p>
      </section>
    </div>
  );
}
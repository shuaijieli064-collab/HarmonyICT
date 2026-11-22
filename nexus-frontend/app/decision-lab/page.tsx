import type { Metadata } from "next";

export const metadata: Metadata = { title: "决策实验室 · NEXUS" };

/** 决策实验室页：方案对比与影响推演（竞赛范围收敛后为非得分模块，骨架先行）。
 */
export default function DecisionLabPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">决策实验室</h1>
        <p className="mt-1 text-sm text-secondary">多方案对比与影响推演</p>
      </header>
      <section
        aria-label="决策推演"
        className="flex min-h-64 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        <p className="text-sm text-muted">
          决策推演开发中（TODO(C, 阶段四): 方案对比与影响推演组件）
        </p>
      </section>
    </div>
  );
}
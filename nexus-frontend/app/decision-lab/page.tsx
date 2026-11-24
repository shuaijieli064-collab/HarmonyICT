import type { Metadata } from "next";
import { FlaskConical } from "lucide-react";
import EngineOverview from "@/components/insight/engine-overview";
import TimelinePanel from "@/components/insight/timeline-panel";
import SimulationPanel from "@/components/insight/simulation-panel";

export const metadata: Metadata = { title: "决策实验室 · NEXUS" };

export default function DecisionLabPage() {
  return (
    <div className="space-y-10">
      <header className="flex items-center gap-2.5">
        <FlaskConical size={22} strokeWidth={1.75} className="text-primary" aria-hidden="true" />
        <div>
          <h1 className="text-xl font-bold text-content">决策实验室</h1>
            <p className="mt-1 text-sm text-secondary">
            洞察引擎族概览 · 未来 30 天投影 · 假设推演沙盘（只读，不产生真实风险）
          </p>
        </div>
      </header>

      <section aria-label="洞察引擎概览" className="space-y-4">
        <h2 className="text-sm font-medium text-secondary">洞察引擎 · 风险分布</h2>
        <EngineOverview />
      </section>

      <section aria-label="未来时间线" className="space-y-4">
        <h2 className="text-sm font-medium text-secondary">未来 30 天会发生什么</h2>
        <TimelinePanel />
      </section>

      <section aria-label="假设推演" className="space-y-4">
        <h2 className="text-sm font-medium text-secondary">假设推演 · 12 个月沙盘</h2>
        <SimulationPanel />
      </section>
    </div>
  );
}

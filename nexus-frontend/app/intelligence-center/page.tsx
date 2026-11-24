import type { Metadata } from "next";
import { Radar } from "lucide-react";
import IntelPanel from "@/components/intel/intel-panel";

export const metadata: Metadata = { title: "情报中心 · NEXUS" };

/** 情报中心页：风险 / 事件 / Agent 执行的统一情报流（GET /api/v1/intel 真实接口）。 */
export default function IntelligenceCenterPage() {
  return (
    <div className="space-y-6">
      <header className="flex items-center gap-2.5">
        <Radar size={22} strokeWidth={1.75} className="text-primary" aria-hidden="true" />
        <div>
          <h1 className="text-2xl font-bold tracking-wide text-content">情报中心</h1>
          <p className="mt-1 text-sm text-secondary">风险、事件与 Agent 动作的统一情报流</p>
        </div>
      </header>
      <IntelPanel />
    </div>
  );
}

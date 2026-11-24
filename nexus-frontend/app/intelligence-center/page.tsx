import type { Metadata } from "next";
import { Radar } from "lucide-react";
import IntelPanel from "@/components/intel/intel-panel";
import RiskTrend from "@/components/risk/risk-trend";
import RiskList from "@/components/risk/risk-list";

export const metadata: Metadata = { title: "情报中心 · NEXUS" };

/** 情报中心页：风险趋势（快照折线）+ 风险列表（依据/派生待办/手动扫描）+ 统一情报流。 */
export default function IntelligenceCenterPage() {
  return (
    <div className="space-y-6">
      <header className="flex items-center gap-2.5">
        <span className="icon-tile"><Radar size={20} strokeWidth={1.75} aria-hidden="true" /></span>
        <div>
          <h1 className="text-2xl font-bold tracking-wide text-content">情报中心</h1>
          <p className="mt-1 text-sm text-secondary">风险、事件与 Agent 动作的统一情报流</p>
        </div>
      </header>
      <RiskTrend />
      <RiskList />
      <IntelPanel />
    </div>
  );
}

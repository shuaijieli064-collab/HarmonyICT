import type { Metadata } from "next";
import RiskList from "@/components/risk/risk-list";

export const metadata: Metadata = { title: "情报中心 · NEXUS" };

/** 情报中心（风险中心，真实接口）：风险等级、触发原因、关联事实依据、确认/解决 */
export default function IntelligenceCenterPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">情报中心</h1>
        <p className="mt-1 text-sm text-secondary">系统主动发现的风险：每条都带触发规则与证据</p>
      </header>
      <RiskList />
    </div>
  );
}
import type { Metadata } from "next";
import { MOCK_RISKS } from "@/mock/dashboard";
import RiskSummaryCard from "@/components/dashboard/risk-summary-card";

export const metadata: Metadata = { title: "情报中心 · NEXUS" };

/** 情报中心（风险中心）：风险等级、触发原因、关联事实、证据来源、建议。
 * 阶段三接入真实风险数据与证据链展示，本轮复用风险卡呈现骨架。
 */
export default function IntelligenceCenterPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">情报中心</h1>
        <p className="mt-1 text-sm text-secondary">系统主动发现的风险：每条都带触发规则与证据</p>
      </header>
      <section aria-label="风险列表" className="grid gap-4 lg:grid-cols-2">
        {MOCK_RISKS.map((risk) => (
          <RiskSummaryCard key={risk.id} risk={risk} />
        ))}
      </section>
    </div>
  );
}
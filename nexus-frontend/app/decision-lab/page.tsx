import type { Metadata } from "next";
import DecisionPanel from "@/components/decision/decision-panel";

export const metadata: Metadata = { title: "决策实验室 · NEXUS" };

/** 决策实验室页（真实接口 + 确定性规则）：方案对比卡由规则计算、结论可追溯，执行走 Agent L2 授权 */
export default function DecisionLabPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">决策实验室</h1>
        <p className="mt-1 text-sm text-secondary">基于世界模型事实的方案对比与影响推演</p>
      </header>
      <DecisionPanel />
    </div>
  );
}
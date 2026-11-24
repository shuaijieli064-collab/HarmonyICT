import type { Metadata } from "next";
import { Bot } from "lucide-react";
import RunsPanel from "@/components/agent/runs-panel";

export const metadata: Metadata = { title: "Agent 中心 · NEXUS" };

/** Agent 中心页：发起执行 → 编排 → L2 授权（契约 8，真实接口） */
export default function AgentCenterPage() {
  return (
    <div className="space-y-6">
      <header className="flex items-center gap-2.5">
        <Bot size={22} strokeWidth={1.75} className="text-primary" aria-hidden="true" />
        <div>
          <h1 className="text-xl font-bold text-content">Agent 中心</h1>
          <p className="mt-1 text-sm text-secondary">授权 Agent 执行任务，全程留痕可回溯</p>
        </div>
      </header>
      <RunsPanel />
    </div>
  );
}
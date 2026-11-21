import type { Metadata } from "next";
import { MOCK_AGENT_TASKS } from "@/mock/dashboard";
import AgentTaskCard from "@/components/dashboard/agent-task-card";

export const metadata: Metadata = { title: "Agent 中心 · NEXUS" };

/** Agent 中心页：任务列表、执行状态、工具调用过程、授权记录、操作日志。
 * 阶段四接入授权交互（L2 操作需确认弹窗）与执行日志，本轮呈现任务列表骨架。
 */
export default function AgentCenterPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">Agent 中心</h1>
        <p className="mt-1 text-sm text-secondary">授权 Agent 执行任务，全程留痕可回溯</p>
      </header>
      <section aria-label="Agent 任务列表" className="space-y-3">
        {MOCK_AGENT_TASKS.map((task) => (
          <AgentTaskCard key={task.id} task={task} />
        ))}
      </section>
    </div>
  );
}
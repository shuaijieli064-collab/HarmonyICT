import StatusCard from "@/components/dashboard/status-card";
import GoalProgressCard from "@/components/dashboard/goal-progress";
import EventTimeline from "@/components/dashboard/event-timeline";
import RiskSummaryCard from "@/components/dashboard/risk-summary-card";
import AgentTaskCard from "@/components/dashboard/agent-task-card";
import TodayActions from "@/components/dashboard/today-actions";
import UploadPanel from "@/components/upload/upload-panel";
import {
  MOCK_ACTIONS,
  MOCK_AGENT_TASKS,
  MOCK_EVENTS,
  MOCK_GOALS,
  MOCK_RISKS,
  MOCK_STATUS_METRICS,
} from "@/mock/dashboard";

/** Dashboard 个人智能驾驶舱：当前状态、目标进度、事件时间线、风险摘要、Agent 任务、今日行动 */
export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">个人智能驾驶舱</h1>
        <p className="mt-1 text-sm text-secondary">一屏看清你的世界状态、风险与今日行动</p>
      </header>

      <section aria-label="当前状态">
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {MOCK_STATUS_METRICS.map((metric) => (
            <StatusCard key={metric.id} metric={metric} />
          ))}
        </div>
      </section>

      <section aria-label="目标进度">
        <h2 className="mb-3 text-base font-semibold text-content">目标进度</h2>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {MOCK_GOALS.map((goal) => (
            <GoalProgressCard key={goal.id} goal={goal} />
          ))}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-label="近期事件">
          <h2 className="mb-3 text-base font-semibold text-content">近期事件</h2>
          <EventTimeline events={MOCK_EVENTS} />
        </section>

        <div className="space-y-6">
          <section aria-label="风险摘要">
            <h2 className="mb-3 text-base font-semibold text-content">风险摘要</h2>
            <div className="space-y-3">
              {MOCK_RISKS.map((risk) => (
                <RiskSummaryCard key={risk.id} risk={risk} />
              ))}
            </div>
          </section>

          <section aria-label="Agent 任务">
            <h2 className="mb-3 text-base font-semibold text-content">Agent 任务</h2>
            <div className="space-y-3">
              {MOCK_AGENT_TASKS.map((task) => (
                <AgentTaskCard key={task.id} task={task} />
              ))}
            </div>
          </section>
        </div>
      </div>

      <section aria-label="今日行动">
        <h2 className="mb-3 text-base font-semibold text-content">今日行动</h2>
        <TodayActions actions={MOCK_ACTIONS} />
      </section>

      <section aria-label="资料上传">
        <h2 className="mb-3 text-base font-semibold text-content">上传资料</h2>
        <UploadPanel />
      </section>
    </div>
  );
}

/* Mock 数据，联调时替换为 services/ 真实接口返回。
 * 禁止写死虚假动态数字充场面：以下数据仅用于阶段一布局开发与演示走查，
 * 联调后全部由后端契约接口返回（见 types/index.ts 对应类型）。
 */

import type {
  ActionItem,
  AgentTask,
  GoalProgress,
  RiskItem,
  StatusMetric,
  WorldEvent,
} from "@/types";

export const MOCK_STATUS_METRICS: StatusMetric[] = [
  { id: "facts", label: "已沉淀事实", value: "128", hint: "来自 36 份资料" },
  { id: "entities", label: "世界实体", value: "47", hint: "设备 / 订阅 / 目标等" },
  { id: "risks", label: "待处理风险", value: "5", hint: "高 1 · 中 3 · 低 1" },
  { id: "tasks", label: "Agent 任务", value: "8", hint: "执行中 2 · 待授权 1" },
];

export const MOCK_GOALS: GoalProgress[] = [
  { id: "g1", title: "《系统设计》学习计划", deadline: "2026-12-20", percent: 62 },
  { id: "g2", title: "毕业论文开题准备", deadline: "2026-11-30", percent: 35 },
  { id: "g3", title: "ICT 大赛作品交付", deadline: "2027-01-15", percent: 20 },
];

export const MOCK_EVENTS: WorldEvent[] = [
  {
    id: "e1",
    title: "新增事实：笔记本购买记录",
    description: "从发票 PDF 抽取：设备 ThinkPad X1，购买日期 2026-08-12，保修 24 个月",
    occurredAt: "2026-11-21 09:32",
    kind: "fact",
  },
  {
    id: "e2",
    title: "触发风险：订阅即将续费",
    description: "规则 R-101：到期前 7 天提醒。云盘订阅将于 11-28 扣费 ¥30/月",
    occurredAt: "2026-11-21 08:15",
    kind: "risk",
  },
  {
    id: "e3",
    title: "Agent 完成：创建待办「确认续费」",
    description: "用户已授权（L2），待办已写入任务中心，执行日志 #a3f2",
    occurredAt: "2026-11-20 21:40",
    kind: "action",
  },
  {
    id: "e4",
    title: "新资料上传：11 月信用卡账单",
    description: "来源已登记，置信度 0.92，待 AI 抽取",
    occurredAt: "2026-11-20 19:05",
    kind: "source",
  },
];

export const MOCK_RISKS: RiskItem[] = [
  {
    id: "r1",
    level: "high",
    title: "学习计划与开题截止日冲突",
    reason: "规则 R-204：目标计划周期覆盖硬性截止日期",
    relatedFactCount: 3,
  },
  {
    id: "r2",
    level: "medium",
    title: "云盘订阅 11-28 自动续费",
    reason: "规则 R-101：订阅到期前 7 天",
    relatedFactCount: 2,
  },
  {
    id: "r3",
    level: "medium",
    title: "笔记本保修登记信息缺失",
    reason: "规则 R-130：设备购买后未登记保修凭证",
    relatedFactCount: 1,
  },
];

export const MOCK_AGENT_TASKS: AgentTask[] = [
  { id: "t1", title: "汇总本月订阅扣费明细", status: "running", updatedAt: "11-21 10:02" },
  { id: "t2", title: "创建待办：确认云盘续费", status: "waiting_auth", updatedAt: "11-21 09:58" },
  { id: "t3", title: "更新设备保修台账", status: "done", updatedAt: "11-20 21:41" },
];

export const MOCK_ACTIONS: ActionItem[] = [
  { id: "a1", title: "确认云盘订阅是否续费（11-28 到期）", from: "风险 R-101", done: false },
  { id: "a2", title: "上传笔记本保修凭证 PDF", from: "风险 R-130", done: false },
  { id: "a3", title: "查看《系统设计》第 4 章学习进度", from: "目标 g1", done: true },
];
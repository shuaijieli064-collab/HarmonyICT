/* 全局类型定义，与后端 Result<T> 契约对应 */

/** 后端统一响应包裹（契约：docs/api-specification.md） */
export interface Result<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

/** 风险等级，与后端风险引擎语义一致 */
export type RiskLevel = "high" | "medium" | "low";

/** 驾驶舱状态指标卡数据 */
export interface StatusMetric {
  id: string;
  label: string;
  value: string;
  hint: string;
}

/** 目标进度 */
export interface GoalProgress {
  id: string;
  title: string;
  deadline: string;
  percent: number;
}

/** 世界模型事件（时间线） */
export interface WorldEvent {
  id: string;
  title: string;
  description: string;
  occurredAt: string;
  kind: "fact" | "risk" | "action" | "source";
}

/** 风险摘要条目（relatedFactCount 为 null 时摘要卡隐藏关联事实行：列表接口无事实数，详情接口才有） */
export interface RiskItem {
  id: string;
  level: RiskLevel;
  title: string;
  reason: string;
  /** 触发规则编码（如 CERT_EXPIRY_30D），展示规则徽章用 */
  ruleCode?: string | null;
  relatedFactCount: number | null;
}

/** Agent 任务条目 */
export interface AgentTask {
  id: string;
  title: string;
  status: "running" | "waiting_auth" | "done" | "failed";
  updatedAt: string;
}

/** 今日行动建议 */
export interface ActionItem {
  id: string;
  title: string;
  from: string;
  done: boolean;
}

/** 上传文件状态 */
export type UploadStatus = "pending" | "uploading" | "extracting" | "done" | "failed";

/** 上传文件条目（sourceId 为契约 4.2 第一步返回，失败重试轮询时复用） */
export interface UploadFileItem {
  id: string;
  name: string;
  size: number;
  progress: number;
  status: UploadStatus;
  sourceId?: string;
}

/** 抽取文件状态（契约 4.3 来源列表 status） */
export type SourceStatus = "pending" | "parsing" | "parsed" | "failed";

/** 分页响应包裹（契约 1 通用约定：records/total/page/pageSize） */
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  pageSize: number;
}

/** 事实值类型（契约 5.2） */
export type FactValueType = "string" | "number" | "date" | "boolean";

/** 事实生命周期状态（契约 5.2） */
export type FactStatus = "active" | "superseded" | "retracted";

/** 事实可信状态（暂定值，以 B 主笔的 extraction-schema.md 为准） */
export type FactConfidenceStatus = "confirmed" | "inferred" | "unverified";

/** 世界模型事实（契约 5.2 响应结构，BIGINT 字段一律 string） */
export interface Fact {
  id: string;
  subjectId: string;
  predicate: string;
  objectValue: string;
  valueType: FactValueType;
  unit: string | null;
  normalizedValue: string;
  evidence: string;
  sourceId: string;
  confidence: number;
  validFrom: string;
  validUntil: string | null;
  status: FactStatus;
  factStatus: FactConfidenceStatus;
  createdAt: string;
  updatedAt: string;
}
/* ===== 以下为联调阶段类型（契约 4/5/6/7/8，后端 DTO 已确认，BIGINT 一律 string） ===== */

/** 来源条目（契约 4.1 响应 + 4.3 列表） */
export interface Source {
  sourceId: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  status: SourceStatus;
  createdAt?: string;
}

/** 世界模型实体类型（契约 5.1） */
export type EntityType = "person" | "asset" | "event" | "document" | "org";

/** 世界模型实体（契约 5.1；attributes 后端为 JSON 字符串，展示前需 JSON.parse） */
export interface Entity {
  id: string;
  entityType: EntityType | string;
  name: string;
  attributes: string;
  createdAt: string;
  updatedAt: string;
}

/** 世界模型事件类型（契约 5.3） */
export type WorldEventType = "education" | "career" | "asset" | "health" | "contract" | "other";

/** 世界模型事件条目（契约 5.3，按 occurredAt 倒序返回） */
export interface EventItem {
  id: string;
  entityId: string | null;
  eventType: WorldEventType | string;
  title: string;
  description: string;
  occurredAt: string;
  sourceId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 风险处理状态（契约 6） */
export type RiskHandleStatus = "open" | "acknowledged" | "resolved";

/** 风险条目（契约 6 列表/详情本体） */
export interface Risk {
  id: string;
  riskType: string;
  level: RiskLevel;
  title: string;
  description: string | null;
  entityId: string | null;
  ruleCode: string | null;
  status: RiskHandleStatus;
  createdAt: string;
  updatedAt: string;
}

/** 风险详情（契约 6：风险本体 + 相关事实快照，展示风险依据） */
export interface RiskDetail {
  risk: Risk;
  relatedFacts: Fact[];
}

/** 任务状态（契约 7：合法流转 todo→in_progress→done；todo/in_progress→cancelled） */
export type TaskStatus = "todo" | "in_progress" | "done" | "cancelled";

/** 任务条目（契约 7；riskId/relatedFactId 为溯源字段，可能为 null） */
export interface Task {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: number;
  dueDate: string | null;
  sourceType: "manual" | "risk" | "agent";
  riskId: string | null;
  /** 来源事实溯源：Agent 基于事实派生任务时由后端回填（新建类动作 targetId 同语义） */
  relatedFactId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Agent 执行状态（后端 DDL chk_run_status） */
export type AgentRunStatus = "running" | "succeeded" | "failed" | "cancelled";

/** Agent 执行记录（契约 8） */
export interface AgentRun {
  id: string;
  triggerType: "manual" | "scheduled" | "event";
  intent: string;
  status: AgentRunStatus;
  summary: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

/** Agent 操作级别（契约 8：L0 只读 / L1 展示 / L2 须授权） */
export type AgentLogLevel = "L0" | "L1" | "L2";

/** Agent 操作日志结果（后端 DDL chk_log_result） */
export type AgentLogResult = "pending" | "success" | "failure" | "denied";

/** Agent 审计摘要（第②线 V3 新增 AgentAuditTrail，随 payload 的 audit 段落库、查询时解析）。
 * 字段以 v3/agent 的 AgentAuditTrail.java 为准，前端不自行造字段、不改 api-spec 主文档。 */
export interface AgentAuditTrail {
  action: string | null;
  authorizedBy: string | null;
  authorizedAt: string | null;
  decision: "approved" | "denied" | null;
  changedField: string | null;
  beforeValue: string | null;
  afterValue: string | null;
  failureReason: string | null;
  executionSummary: string | null;
}

/** Agent 操作日志（契约 8；payload 为 JSON 字符串，展示前需 JSON.parse）
 * V3：audit 为后端从 payload.audit 解析出的结构化审计摘要，旧数据可能为 null。 */
export interface AgentActionLog {
  id: string;
  actionType: string;
  level: AgentLogLevel;
  targetType: string | null;
  targetId: string | null;
  payload: string;
  result: AgentLogResult;
  authorized: boolean;
  createdAt: string;
  audit?: AgentAuditTrail | null;
}

/** Agent 执行详情（契约 8：run 本体 + 操作日志列表） */
export interface RunDetail {
  run: AgentRun;
  logs: AgentActionLog[];
}

/* ===== 资产中心 / 情报中心（聚合视图，BIGINT 字段一律 string） ===== */

/** 资产关联事实（GET /api/v1/assets 的 facts 字段，来自 world_facts active 状态） */
export interface AssetFact {
  factId: string;
  predicate: string;
  objectValue: string;
  valueType: FactValueType | string;
  unit: string | null;
  normalizedValue: string | null;
  validUntil: string | null;
}

/** 资产条目：世界实体 + 其关联事实聚合 */
export interface Asset {
  id: string;
  entityType: EntityType | string;
  name: string;
  attributes: string;
  createdAt: string;
  facts: AssetFact[];
}

/** 资产汇总（GET /api/v1/assets/summary） */
export interface AssetSummary {
  totalAssets: number;
  byType: Record<string, number>;
  totalValue: number;
  valueUnit: string | null;
  openRiskCount: number;
}

/** 情报中心汇总（GET /api/v1/intel/summary） */
export interface IntelSummary {
  openRiskCount: number;
  riskByLevel: Record<string, number>;
  recentEventCount: number;
  pendingTaskCount: number;
  recentAgentRunCount: number;
}

/** 情报流条目类型 */
export type IntelFeedType = "risk" | "event" | "agent";

/** 情报流条目（风险/事件/Agent 执行的统一动态） */
export interface IntelFeedItem {
  type: IntelFeedType | string;
  refId: string;
  title: string | null;
  description: string | null;
  /** 仅风险条目有值（low/medium/high） */
  level: RiskLevel | string | null;
  status: string | null;
  occurredAt: string | null;
}

/* ===== 通知中心 / 驾驶舱聚合（体量扩充新增，BIGINT 字段一律 string） ===== */

/** 通知类别 */
export type NotificationCategory = "risk" | "agent" | "system";

/** 通知条目（GET /api/v1/notifications；readAt 为 null 即未读） */
export interface NotificationItem {
  id: string;
  category: NotificationCategory | string;
  title: string;
  content: string | null;
  refType: string | null;
  refId: string | null;
  read: boolean;
  createdAt: string;
  readAt: string | null;
}

/** 驾驶舱聚合统计（GET /api/v1/dashboard/summary） */
export interface DashboardSummary {
  entityTotal: number;
  factTotal: number;
  eventTotal: number;
  openRiskTotal: number;
  taskTotal: number;
  todoTaskTotal: number;
  agentRunTotal: number;
  unreadNotificationTotal: number;
  riskByLevel: { level: RiskLevel | string; count: number }[];
  recentEvents: {
    id: string;
    title: string;
    eventType: string;
    occurredAt: string | null;
  }[];
  topRisks: {
    id: string;
    level: RiskLevel | string;
    title: string;
    ruleCode: string | null;
    status: string;
  }[];
}

/* ===== 体量扩充第二波：图谱 / 周报 / 导出 ===== */

/** 世界模型图谱节点（GET /api/v1/world/graph；kind ∈ entity|fact|event|risk） */
export interface WorldGraphNode {
  id: string;
  kind: "entity" | "fact" | "event" | "risk" | string;
  name: string;
  /** entity 节点为实体类型，其余为节点种类 */
  category: string;
  meta: string;
}

/** 世界模型图谱边（kind ∈ fact_of|event_of|co_source|risk_on） */
export interface WorldGraphLink {
  source: string;
  target: string;
  kind: "fact_of" | "event_of" | "co_source" | "risk_on" | string;
  label: string;
}

/** 世界模型图谱聚合响应 */
export interface WorldGraphResponse {
  nodes: WorldGraphNode[];
  links: WorldGraphLink[];
}

/** 个人周期报告（GET /api/v1/reports/weekly） */
export interface WeeklyReport {
  periodStart: string;
  periodEnd: string;
  days: number;
  newFactCount: number;
  newEventCount: number;
  newRiskCount: number;
  taskCompletedCount: number;
  agentRunCount: number;
  notificationCount: number;
  riskByLevel: { level: RiskLevel | string; count: number }[];
  dailyTrend: { day: string; facts: number; events: number }[];
  notableRisks: {
    id: string;
    level: RiskLevel | string;
    title: string;
    ruleCode: string | null;
  }[];
}
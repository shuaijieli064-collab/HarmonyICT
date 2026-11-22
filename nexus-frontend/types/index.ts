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

/** 上传文件条目 */
export interface UploadFileItem {
  id: string;
  name: string;
  size: number;
  progress: number;
  status: UploadStatus;
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

/** Agent 操作日志（契约 8；payload 为 JSON 字符串，展示前需 JSON.parse） */
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
}

/** Agent 执行详情（契约 8：run 本体 + 操作日志列表） */
export interface RunDetail {
  run: AgentRun;
  logs: AgentActionLog[];
}
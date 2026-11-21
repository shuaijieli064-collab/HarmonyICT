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

/** 风险摘要条目 */
export interface RiskItem {
  id: string;
  level: RiskLevel;
  title: string;
  reason: string;
  relatedFactCount: number;
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
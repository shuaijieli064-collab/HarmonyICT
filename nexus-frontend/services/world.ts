/* 世界模型服务（契约 docs/api-specification.md 5，已切真实接口）：
 * 实体/事实/事件 CRUD。attributes 为后端序列化的 JSON 字符串，
 * 展示侧用 parseEntityAttributes 解析，解析失败返回空对象兜底。
 */

import { request } from "@/lib/http";
import type { Entity, EventItem, Fact, PageResult } from "@/types";

export interface ListOptions {
  page?: number;
  pageSize?: number;
}

/** 实体列表（契约 5.1：entityType 可选 / keyword 按 name 模糊） */
export function listEntities(options: ListOptions & { entityType?: string; keyword?: string } = {}) {
  return request<PageResult<Entity>>("/api/v1/entities", { query: options });
}

/** 事实列表（契约 5.2：subjectId/predicate/sourceId 过滤，status 默认 active） */
export function listFacts(
  options: ListOptions & { subjectId?: string; predicate?: string; sourceId?: string; status?: string } = {}
) {
  return request<PageResult<Fact>>("/api/v1/facts", { query: options });
}

/** 事件列表（契约 5.3：按 occurredAt 倒序） */
export function listEvents(
  options: ListOptions & { eventType?: string; entityId?: string } = {}
) {
  return request<PageResult<EventItem>>("/api/v1/events", { query: options });
}

/** 创建实体（契约 5.1）：实体重名返回 code=20104，UI 层用
 * `err instanceof ApiError && err.code === 20104` 区分提示「已存在同名实体」。
 */
export function createEntity(input: { entityType: string; name: string; attributes?: Record<string, unknown> }) {
  return request<Entity>("/api/v1/entities", { method: "POST", body: input });
}

/** 创建事实（契约 5.2） */
export function createFact(input: {
  subjectId: string;
  predicate: string;
  objectValue: string;
  sourceId?: string;
  validFrom?: string;
  validUntil?: string;
  valueType?: string;
  unit?: string;
  normalizedValue?: string;
  evidence?: string;
}) {
  return request<Fact>("/api/v1/facts", { method: "POST", body: input });
}

/** 创建事件（契约 5.3） */
export function createEvent(input: {
  entityId?: string;
  eventType: string;
  title: string;
  description?: string;
  occurredAt: string;
  sourceId?: string;
}) {
  return request<EventItem>("/api/v1/events", { method: "POST", body: input });
}

/** 解析实体 attributes JSON 字符串（后端为 String 存储，失败兜底空对象） */
export function parseEntityAttributes(attributes: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(attributes) as unknown;
    return typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}
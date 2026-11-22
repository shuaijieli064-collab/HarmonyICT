/* 任务服务（契约 docs/api-specification.md 7，已切真实接口）：
 * CRUD 与状态流转（todo→in_progress→done；todo/in_progress→cancelled，非法流转 50102）。
 */

import { request } from "@/lib/http";
import type { PageResult, Task } from "@/types";

export interface ListTasksOptions {
  page?: number;
  pageSize?: number;
  status?: string;
  sourceType?: string;
}

/** 任务列表（契约 7：优先按 dueDate 升序） */
export function listTasks(options: ListTasksOptions = {}) {
  return request<PageResult<Task>>("/api/v1/tasks", { query: options });
}

/** 创建任务（契约 7：priority 0 普通/1 重要/2 紧急；relatedFactId 须存在且属于当前用户，否则 20102） */
export function createTask(input: {
  title: string;
  description?: string;
  priority?: number;
  dueDate?: string;
  sourceType?: string;
  riskId?: string;
  relatedFactId?: string;
}) {
  return request<Task>("/api/v1/tasks", { method: "POST", body: input });
}

/** 更新任务（契约 7） */
export function updateTask(
  id: string,
  input: { title?: string; description?: string; priority?: number; dueDate?: string }
) {
  return request<Task>(`/api/v1/tasks/${id}`, { method: "PUT", body: input });
}

/** 状态流转（契约 7：非法流转返回 code=50102） */
export function updateTaskStatus(id: string, status: string) {
  return request<Task>(`/api/v1/tasks/${id}/status`, { method: "PUT", body: { status } });
}

/** 删除任务（契约 7） */
export function deleteTask(id: string) {
  return request<null>(`/api/v1/tasks/${id}`, { method: "DELETE" });
}
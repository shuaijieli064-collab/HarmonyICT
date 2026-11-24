/* 通知服务（体量扩充新增）：列表、未读数、已读流转。
 * 对应后端 /api/v1/notifications，PUT 全部幂等可安全重试。
 */

import { request } from "@/lib/http";
import type { NotificationItem, PageResult } from "@/types";

export interface ListNotificationsOptions {
  page?: number;
  pageSize?: number;
  /** true 只看未读 */
  unread?: boolean;
}

/** 通知列表（按创建时间倒序） */
export function listNotifications(options: ListNotificationsOptions = {}) {
  return request<PageResult<NotificationItem>>("/api/v1/notifications", { query: options });
}

/** 未读数量（顶栏铃铛徽标） */
export function getUnreadCount() {
  return request<{ count: number }>("/api/v1/notifications/unread-count");
}

/** 标记单条已读（幂等：已读直接返回原状态） */
export function markNotificationRead(id: string) {
  return request<NotificationItem>(`/api/v1/notifications/${id}/read`, { method: "PUT" });
}

/** 全部标记已读，返回受影响条数 */
export function markAllNotificationsRead() {
  return request<{ updated: number }>("/api/v1/notifications/read-all", { method: "PUT" });
}

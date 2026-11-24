/* 周期报告服务（体量扩充第二波）：GET /api/v1/reports/weekly。 */

import { request } from "@/lib/http";
import type { WeeklyReport } from "@/types";

/** 个人周期报告（days 默认 7，1–31） */
export function getWeeklyReport(days = 7) {
  return request<WeeklyReport>("/api/v1/reports/weekly", { query: { days } });
}

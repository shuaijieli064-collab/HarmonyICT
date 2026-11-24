/* 数据导出服务（体量扩充第二波）：
 * CSV 下载走 blob（后端返回 text/csv 附件而非 Result 包裹），
 * 请求仍携带 Authorization，与 JSON 接口共用认证语义。
 */

import { API_BASE_URL } from "@/lib/http";
import { getToken } from "@/services/auth";

export type ExportDataset = "facts" | "risks" | "tasks" | "entities";

/** 触发浏览器下载当前用户指定数据集的 CSV */
export async function downloadCsv(dataset: ExportDataset, fileName?: string): Promise<void> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(new URL(`/api/v1/export/${dataset}`, API_BASE_URL), { headers });
  if (!response.ok) {
    throw new Error(`导出失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName ?? `nexus-${dataset}.csv`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

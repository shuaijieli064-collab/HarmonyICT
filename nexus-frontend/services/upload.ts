/* 上传与抽取结果轮询（契约 docs/api-specification.md 4.1/4.2/5.2，已切真实接口）：
 * - 上传 POST /api/v1/sources/upload（multipart），仅落库并返回雪花字符串 sourceId；
 * - 轮询 GET /api/v1/facts?sourceId=<id>&status=active（2s 间隔 / 60s 超时），
 *   超时后查来源详情区分「抽取失败（status=failed）」与「仍在解析」。
 */

import { request } from "@/lib/http";
import { UPLOAD_ACCEPT, UPLOAD_MAX_SIZE, UPLOAD_MAX_SIZE_MB } from "@/lib/constants";
import type { Fact } from "@/types";

export type UploadPhase = "uploading" | "extracting";

type ProgressCallback = (progress: number, phase: UploadPhase) => void;

/** 契约 4.2：轮询间隔建议 2s，超时上限 60s */
const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 60000;

const POLL_PAGE_SIZE = 100;

/** 上传响应（契约 4.1）：sourceId 为后端分配的雪花数字字符串，禁止自造 UUID */
export interface UploadResponse {
  sourceId: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  status: "pending";
}

/** 客户端预校验（契约 4.1：单文件 ≤ 20MB，pdf/docx/xlsx/txt/md/png/jpg）：
 * 扩展名白名单先行拦截，避免不合规文件上传完才被后端 30102 拒绝。
 */
export function validateUploadFile(file: File): string | null {
  const lowerName = file.name.toLowerCase();
  const allowedExtensions = UPLOAD_ACCEPT.split(",");
  if (!allowedExtensions.some((ext) => lowerName.endsWith(ext))) {
    return `不支持的文件类型「${file.name}」，仅限 pdf / docx / xlsx / txt / md / png / jpg`;
  }
  if (file.size > UPLOAD_MAX_SIZE) {
    return `文件「${file.name}」超过大小上限 ${UPLOAD_MAX_SIZE_MB}MB`;
  }
  if (file.size === 0) {
    return `文件「${file.name}」内容为空`;
  }
  return null;
}

/** 契约 4.1：上传 multipart，XHR 实现（fetch 无法上报上传进度） */
function requestUpload(file: File, onProgress: ProgressCallback): Promise<UploadResponse> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append("file", file);
    const xhr = new XMLHttpRequest();
    xhr.open("POST", new URL("/api/v1/sources/upload", process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"));

    const token = window.localStorage.getItem("nexus_token");
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);

    xhr.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100), "uploading");
      }
    });
    xhr.addEventListener("load", () => {
      try {
        const envelope = JSON.parse(xhr.responseText) as {
          code: number;
          message: string;
          data: UploadResponse | null;
        };
        if (envelope.code === 0 && envelope.data) {
          resolve(envelope.data);
        } else {
          reject(new Error(envelope.message || "上传失败"));
        }
      } catch {
        reject(new Error(`响应解析失败（HTTP ${xhr.status}）`));
      }
    });
    xhr.addEventListener("error", () => reject(new Error("网络异常，请检查后端服务是否可用")));
    xhr.send(form);
  });
}

/** 上传文件并返回 sourceId（契约 4.1 + 4.2 第一步） */
export async function uploadFile(file: File, onProgress: ProgressCallback): Promise<string> {
  const invalidReason = validateUploadFile(file);
  if (invalidReason) throw new Error(invalidReason);
  onProgress(0, "uploading");
  const response = await requestUpload(file, onProgress);
  return response.sourceId;
}

/** 分页响应包裹（契约 1 通用约定） */
interface PageEnvelope<T> {
  records: T[];
  total: number;
}

async function fetchSourceFacts(sourceId: string): Promise<PageEnvelope<Fact>> {
  return request<PageEnvelope<Fact>>("/api/v1/facts", {
    query: { sourceId, status: "active", page: 1, pageSize: POLL_PAGE_SIZE },
  });
}

/** 轮询抽取结果（契约 4.2 第二步）：sourceId 名下出现 active 事实即完成；
 * 超时后查来源详情：status=failed 明确失败，否则提示稍后查看。
 */
export async function pollExtraction(
  sourceId: string,
  onProgress: ProgressCallback
): Promise<Fact[]> {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const page = await fetchSourceFacts(sourceId);
    if (page.records.length > 0) {
      onProgress(100, "extracting");
      return page.records;
    }
    onProgress(Math.min(95, Math.round(((POLL_TIMEOUT_MS - (deadline - Date.now())) / POLL_TIMEOUT_MS) * 100)), "extracting");
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  const source = await request<{ status?: string }>(`/api/v1/sources/${sourceId}`);
  if (source.status === "failed") {
    throw new Error("AI 抽取失败，请检查文件内容后重新上传");
  }
  throw new Error("抽取超时，请稍后在来源列表查看状态");
}
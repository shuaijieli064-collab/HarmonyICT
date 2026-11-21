/* API 请求统一封装：上传与抽取结果轮询（契约 docs/api-specification.md 4.1/4.2/5.2）。
 * 契约已定稿候选：上传接口只落库并返回 sourceId（status=pending），抽取由 B 的
 * AI 引擎后台异步完成；前端按 2s 间隔轮询 GET /api/v1/facts?sourceId=<id>&status=active，
 * 60s 超时后应查来源详情 status=failed 提示失败（4.2）。
 * 当前为 Mock 实现：联调时仅替换本文件实现，组件层不感知
 * （符合代码规范 3.3：请求统一走 services/）。
 */

import type { Fact } from "@/types";
import { MOCK_UPLOADED_FACTS } from "@/mock/extraction";

export type UploadPhase = "uploading" | "extracting";

type ProgressCallback = (progress: number, phase: UploadPhase) => void;

/** 契约 4.2：轮询间隔建议 2s，超时上限 60s */
const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 60000;

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 上传文件，仅落库并返回后端分配的雪花数字字符串 sourceId（契约 4.1，禁止自造 UUID） */
export async function uploadFile(file: File, onProgress: ProgressCallback): Promise<string> {
  // TODO(C, 阶段五): 对接 POST /api/v1/sources/upload（multipart/form-data），替换 Mock 实现
  for (let progress = 0; progress <= 100; progress += 20) {
    await delay(150);
    onProgress(progress, "uploading");
  }
  return `1948${Date.now()}`;
}

/** 轮询抽取结果：sourceId 名下出现事实即视为抽取完成（契约 4.2 异步两步） */
export async function pollExtraction(
  sourceId: string,
  onProgress: ProgressCallback
): Promise<Fact[]> {
  // TODO(C, 阶段五): 对接 GET /api/v1/facts?sourceId=<sourceId>&status=active（2s 间隔），
  // 超时后查 GET /api/v1/sources/{sourceId} 的 status=failed 并抛错
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  let round = 0;
  while (Date.now() < deadline) {
    await delay(POLL_INTERVAL_MS);
    round += 1;
    onProgress(Math.min(round * 25, 95), "extracting");
    // Mock：模拟 AI 引擎异步抽取，第二轮轮询后产生事实
    if (round >= 2) {
      onProgress(100, "extracting");
      return MOCK_UPLOADED_FACTS.map((fact) => ({ ...fact, sourceId }));
    }
  }
  throw new Error("抽取超时，请稍后在来源列表查看状态");
}
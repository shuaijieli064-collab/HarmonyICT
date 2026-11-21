/* API 请求统一封装：上传接口。
 * 契约 docs/api-specification.md 4.1 已定稿候选，当前为 Mock 实现：
 * 模拟「上传 → AI 抽取」两段进度，返回抽取结果摘要（联调时上传响应与
 * 抽取结果将拆为两步：POST /sources/upload → status=parsed 后查询事实列表）。
 * 联调时仅替换本文件实现，组件层不感知（符合代码规范 3.3：请求统一走 services/）。
 */

import type { ExtractResult } from "@/types";

type ProgressCallback = (progress: number, status: "uploading" | "extracting") => void;

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 上传文件并返回 AI 抽取结果摘要 */
export async function uploadFile(file: File, onProgress: ProgressCallback): Promise<ExtractResult> {
  // TODO(C, 阶段五): 对接契约上传接口 POST /api/v1/sources/upload，替换 Mock 实现
  for (let progress = 0; progress <= 100; progress += 20) {
    await delay(150);
    onProgress(progress, "uploading");
  }
  onProgress(100, "extracting");
  await delay(800);

  return {
    // 雪花 ID 风格字符串（契约约定 BIGINT 一律序列化为 string，前端按 string 处理）
    sourceId: `1948${Date.now().toString()}`,
    factCount: 3,
    entities: ["设备", "订阅", "目标"],
    confidence: 0.92,
  };
}
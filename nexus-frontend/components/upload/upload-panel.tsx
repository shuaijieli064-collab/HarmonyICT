"use client";

import { useCallback, useId, useRef, useState } from "react";
import { cn, formatSize } from "@/lib/utils";
import { UPLOAD_ACCEPT, UPLOAD_MAX_SIZE } from "@/lib/constants";
import { uploadFile } from "@/services/upload";
import type { ExtractResult, UploadFileItem } from "@/types";

/** 文件上传交互组件：拖拽 + 点击选择 + 进度 + 抽取结果展示
 * 契约未就绪阶段走 services/upload.ts 的 Mock 实现，联调时无缝切换。
 */
export default function UploadPanel() {
  const dropZoneId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [files, setFiles] = useState<UploadFileItem[]>([]);
  const [results, setResults] = useState<Record<string, ExtractResult>>({});
  const [error, setError] = useState<string | null>(null);

  const startUpload = useCallback(
    (fileList: FileList) => {
      setError(null);
      for (const file of Array.from(fileList)) {
        if (file.size > UPLOAD_MAX_SIZE) {
          setError(`文件「${file.name}」超过大小上限 10MB`);
          continue;
        }
        const id = `${Date.now()}-${file.name}`;
        const item: UploadFileItem = {
          id,
          name: file.name,
          size: file.size,
          progress: 0,
          status: "uploading",
        };
        setFiles((prev) => [...prev, item]);
        void uploadFile(file, (progress, status) => {
          setFiles((prev) =>
            prev.map((f) => (f.id === id ? { ...f, progress, status } : f))
          );
        })
          .then((result) => setResults((prev) => ({ ...prev, [id]: result })))
          .catch(() =>
            setFiles((prev) =>
              prev.map((f) => (f.id === id ? { ...f, status: "failed" as const } : f))
            )
          );
      }
    },
    []
  );

  const onDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files.length > 0) startUpload(e.dataTransfer.files);
  };

  const STATUS_TEXT: Record<UploadFileItem["status"], string> = {
    pending: "等待中",
    uploading: "上传中",
    extracting: "AI 抽取中",
    done: "已完成",
    failed: "失败",
  };

  return (
    <div className="space-y-4">
      <div
        id={dropZoneId}
        role="button"
        tabIndex={0}
        aria-label="上传资料文件"
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") inputRef.current?.click();
        }}
        className={cn(
          "flex min-h-32 cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-6 text-center transition-colors",
          dragOver
            ? "border-primary bg-primary/5"
            : "border-secondary/30 bg-card hover:border-primary/60"
        )}
      >
        <p className="text-sm text-content">拖拽文件到此处，或点击选择</p>
        <p className="text-xs text-muted">支持 PDF / 图片（PNG、JPG）/ TXT，单个不超过 10MB</p>
        <input
          ref={inputRef}
          type="file"
          accept={UPLOAD_ACCEPT}
          multiple
          className="hidden"
          onChange={(e) => {
            if (e.target.files && e.target.files.length > 0) startUpload(e.target.files);
            e.target.value = "";
          }}
        />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {files.map((file) => (
        <div key={file.id} className="rounded-xl border border-secondary/10 bg-card p-4">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-content">{file.name}</p>
              <p className="text-xs text-muted">{formatSize(file.size)}</p>
            </div>
            <span
              className={cn(
                "shrink-0 text-xs",
                file.status === "failed" && "text-danger",
                file.status === "done" && "text-success"
              )}
            >
              {STATUS_TEXT[file.status]}
            </span>
          </div>
          {(file.status === "uploading" || file.status === "extracting") && (
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-base">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{ width: `${file.progress}%` }}
                role="progressbar"
                aria-valuenow={file.progress}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`上传进度：${file.name}`}
              />
            </div>
          )}
          {results[file.id] && (
            <div className="mt-3 rounded-lg bg-base p-3 text-xs leading-relaxed text-secondary">
              <p>
                抽取完成：<span className="text-content">{results[file.id].factCount} 条事实</span>
                ，实体：{results[file.id].entities.join("、")}
              </p>
              <p className="mt-1 text-muted">
                来源已登记，置信度 {results[file.id].confidence}（来源 ID：{results[file.id].sourceId}）
              </p>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
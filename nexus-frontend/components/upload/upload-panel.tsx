"use client";

import { useCallback, useId, useRef, useState } from "react";
import { cn, formatSize } from "@/lib/utils";
import { FACT_STATUS_MAP, UPLOAD_ACCEPT, UPLOAD_MAX_SIZE, UPLOAD_MAX_SIZE_MB } from "@/lib/constants";
import { pollExtraction, uploadFile } from "@/services/upload";
import type { Fact, UploadFileItem } from "@/types";

/** 文件上传交互组件：拖拽 + 点击选择 + 两段进度 + 事实结果展示
 * 契约 4.2 异步两步：上传拿 sourceId → 轮询事实（2s 间隔），当前走 services/upload.ts 的 Mock 实现，联调时无缝切换。
 */
export default function UploadPanel() {
  const dropZoneId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [files, setFiles] = useState<UploadFileItem[]>([]);
  const [results, setResults] = useState<Record<string, Fact[]>>({});
  const [error, setError] = useState<string | null>(null);

  const updateFile = useCallback((id: string, patch: Partial<UploadFileItem>) => {
    setFiles((prev) => prev.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  }, []);

  const startUpload = useCallback(
    (fileList: FileList) => {
      setError(null);
      for (const file of Array.from(fileList)) {
        if (file.size > UPLOAD_MAX_SIZE) {
          setError(`文件「${file.name}」超过大小上限 ${UPLOAD_MAX_SIZE_MB}MB`);
          continue;
        }
        const id = crypto.randomUUID();
        setFiles((prev) => [
          ...prev,
          { id, name: file.name, size: file.size, progress: 0, status: "uploading" },
        ]);
        void (async () => {
          try {
            // 契约 4.2 第一步：上传仅落库，返回雪花数字字符串 sourceId
            const sourceId = await uploadFile(file, (progress, phase) => {
              updateFile(id, { progress, status: phase === "uploading" ? "uploading" : "extracting" });
            });
            // 契约 4.2 第二步：轮询 sourceId 名下事实，出现即抽取完成
            const facts = await pollExtraction(sourceId, (progress) => {
              updateFile(id, { progress, status: "extracting" });
            });
            updateFile(id, { status: "done" });
            setResults((prev) => ({ ...prev, [id]: facts }));
          } catch {
            updateFile(id, { status: "failed" });
          }
        })();
      }
    },
    [updateFile]
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
        <p className="text-xs text-muted">
          支持 PDF / Word / Excel / TXT / Markdown / 图片（PNG、JPG），单个不超过 {UPLOAD_MAX_SIZE_MB}MB
        </p>
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
            <div className="mt-3 space-y-2">
              <p className="text-xs text-secondary">
                抽取完成：<span className="text-content">{results[file.id].length} 条事实</span>
              </p>
              {results[file.id].map((fact) => (
                <div key={fact.id} className="rounded-lg bg-base p-3 text-xs leading-relaxed">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-content">
                      {fact.objectValue}
                      {fact.unit ? ` ${fact.unit}` : ""}
                    </span>
                    <span
                      className={cn(
                        "shrink-0 rounded-full px-2 py-0.5",
                        FACT_STATUS_MAP[fact.factStatus].bg,
                        FACT_STATUS_MAP[fact.factStatus].text
                      )}
                    >
                      {FACT_STATUS_MAP[fact.factStatus].label}
                    </span>
                  </div>
                  <p className="mt-1 text-muted">
                    置信度 {fact.confidence}
                    {fact.evidence ? ` · ${fact.evidence}` : ""}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
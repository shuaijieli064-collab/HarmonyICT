"use client";

import { useState } from "react";
import { Download, LoaderCircle } from "lucide-react";
import { downloadCsv, type ExportDataset } from "@/services/export";

/** CSV 导出按钮（体量扩充第二波）：blob 下载，失败显示行内错误，不打断页面。 */
export default function ExportButton({ dataset, label }: { dataset: ExportDataset; label?: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async () => {
    setBusy(true);
    setError(null);
    try {
      await downloadCsv(dataset);
    } catch (err) {
      setError(err instanceof Error ? err.message : "导出失败");
    } finally {
      setBusy(false);
    }
  };

  return (
    <span className="inline-flex items-center gap-2">
      <button
        type="button"
        onClick={handleDownload}
        disabled={busy}
        className="flex items-center gap-1.5 rounded-lg border border-secondary/20 px-3 py-1.5 text-xs text-secondary transition-all duration-200 hover:border-primary/40 hover:text-primary active:scale-[0.97] disabled:opacity-50"
      >
        {busy ? (
          <LoaderCircle size={13} strokeWidth={2} className="animate-spin" aria-hidden="true" />
        ) : (
          <Download size={13} strokeWidth={1.75} aria-hidden="true" />
        )}
        {busy ? "导出中…" : (label ?? `导出 ${dataset}.csv`)}
      </button>
      {error && <span className="text-xs text-danger">{error}</span>}
    </span>
  );
}

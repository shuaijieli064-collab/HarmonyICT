import type { Metadata } from "next";
import { Boxes } from "lucide-react";
import AssetPanel from "@/components/asset/asset-panel";

export const metadata: Metadata = { title: "资产中心 · NEXUS" };

/** 资产中心页：设备、订阅、预算等个人资产的台账视图（GET /api/v1/assets 真实接口）。 */
export default function AssetCenterPage() {
  return (
    <div className="space-y-6">
      <header className="flex items-center gap-2.5">
        <Boxes size={22} strokeWidth={1.75} className="text-primary" aria-hidden="true" />
        <div>
          <h1 className="text-2xl font-bold tracking-wide text-content">资产中心</h1>
          <p className="mt-1 text-sm text-secondary">设备、订阅与预算的统一台账（数据来自世界模型实体）</p>
        </div>
      </header>
      <AssetPanel />
    </div>
  );
}

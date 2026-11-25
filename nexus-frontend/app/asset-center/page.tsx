import type { Metadata } from "next";
import AssetPanel from "@/components/asset/asset-panel";

export const metadata: Metadata = { title: "资产中心 · NEXUS" };

/** 资产中心页（真实接口）：按实体聚合事实的资产台账，validUntil 有效期倒计时标记 */
export default function AssetCenterPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">资产中心</h1>
        <p className="mt-1 text-sm text-secondary">设备、订阅与预算的统一台账</p>
      </header>
      <AssetPanel />
    </div>
  );
}
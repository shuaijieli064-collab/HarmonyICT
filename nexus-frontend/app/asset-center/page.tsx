import type { Metadata } from "next";
import AssetTable from "@/components/asset/asset-table";

export const metadata: Metadata = { title: "资产中心 · NEXUS" };

export default function AssetCenterPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">资产中心</h1>
        <p className="mt-1 text-sm text-secondary">设备、订阅与预算的统一台账（数据来自世界模型实体）</p>
      </header>
      <AssetTable />
    </div>
  );
}

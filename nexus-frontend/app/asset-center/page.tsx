import type { Metadata } from "next";

export const metadata: Metadata = { title: "资产中心 · NEXUS" };

/** 资产中心页：设备、订阅、预算等个人资产的台账视图。
 * 阶段二随世界模型数据结构定稿后实现台账列表与详情。
 */
export default function AssetCenterPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-content">资产中心</h1>
        <p className="mt-1 text-sm text-secondary">设备、订阅与预算的统一台账</p>
      </header>
      <section
        aria-label="资产台账"
        className="flex min-h-64 items-center justify-center rounded-xl border border-secondary/10 bg-card"
      >
        <p className="text-sm text-muted">
          资产台账开发中（TODO(C, 阶段二): 依赖世界模型实体数据结构定稿）
        </p>
      </section>
    </div>
  );
}
"use client";

import { memo, useEffect, useRef } from "react";
import { animate, useMotionValue } from "framer-motion";
import type { StatusMetric } from "@/types";

/** 指标数字滚动（count-up）：useMotionValue + animate 驱动，不经过 React 渲染循环。
 * memo 隔离：数值变化只重绘本组件文本，不触发父级列表重渲染。
 */
function CountUpText({ value }: { value: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const mv = useMotionValue(0);

  useEffect(() => {
    const controls = animate(mv, value, {
      duration: 0.8,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (latest) => {
        if (ref.current) {
          ref.current.textContent = String(Math.round(latest));
        }
      },
    });
    return () => controls.stop();
  }, [value, mv]);

  return <span ref={ref}>0</span>;
}

const MemoCountUp = memo(CountUpText);

/** 驾驶舱状态指标卡（玻璃面 + 数字滚动动效） */
function StatusCardInner({ metric }: { metric: StatusMetric }) {
  const numeric = Number(metric.value);
  const isNumeric = Number.isFinite(numeric) && metric.value.trim() !== "";
  return (
    <div className="glass-card shimmer rounded-xl p-4">
      <p className="text-sm text-secondary">{metric.label}</p>
      <p className="mt-1 text-3xl font-bold tabular-nums text-content">
        {isNumeric ? <MemoCountUp value={numeric} /> : metric.value}
      </p>
      <p className="mt-1 text-xs text-muted">{metric.hint}</p>
    </div>
  );
}

const StatusCard = memo(StatusCardInner);
export default StatusCard;

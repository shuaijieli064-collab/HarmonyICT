"use client";

import { memo, useEffect, useRef } from "react";
import { animate, useMotionValue } from "framer-motion";
import { HeartPulse } from "lucide-react";
import type { HealthScore } from "@/types";

/** 健康度环形仪表：SVG stroke-dasharray 动画（transform/opacity 合成层）。
 * 等级语义色与全站一致：excellent=success / good=primary / attention=warning / critical=danger。
 */

const LEVEL_STYLE: Record<string, { label: string; color: string }> = {
  excellent: { label: "优秀", color: "var(--success)" },
  good: { label: "良好", color: "var(--accent)" },
  attention: { label: "需关注", color: "var(--warning)" },
  critical: { label: "告警", color: "var(--danger)" },
};

const RADIUS = 44;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function GaugeNumber({ value }: { value: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const mv = useMotionValue(0);
  useEffect(() => {
    const controls = animate(mv, value, {
      duration: 0.9,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (latest) => {
        if (ref.current) ref.current.textContent = String(Math.round(latest));
      },
    });
    return () => controls.stop();
  }, [value, mv]);
  return <span ref={ref}>0</span>;
}

const MemoGaugeNumber = memo(GaugeNumber);

function HealthGaugeInner({ health }: { health: HealthScore }) {
  const style = LEVEL_STYLE[health.level] ?? LEVEL_STYLE.attention;
  const ratio = Math.max(0, Math.min(1, health.score / 100));
  return (
    <div className="card-elevated flex flex-wrap items-center gap-5 rounded-xl p-5">
      <div className="relative h-28 w-28 shrink-0" role="img" aria-label={`健康度 ${health.score} 分，${style.label}`}>
        <svg viewBox="0 0 100 100" className="h-full w-full -rotate-90">
          <circle cx="50" cy="50" r={RADIUS} fill="none" stroke="var(--border-glass)" strokeWidth="8" />
          <circle
            cx="50"
            cy="50"
            r={RADIUS}
            fill="none"
            stroke={style.color}
            strokeWidth="8"
            strokeLinecap="round"
            strokeDasharray={CIRCUMFERENCE}
            strokeDashoffset={CIRCUMFERENCE * (1 - ratio)}
            style={{
              transition: "stroke-dashoffset 0.9s cubic-bezier(0.16,1,0.3,1), stroke 0.3s ease-out",
              filter: `drop-shadow(0 0 6px ${style.color})`,
            }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-3xl font-bold tabular-nums text-content">
            <MemoGaugeNumber value={health.score} />
          </span>
          <span className="text-[10px] text-muted">/ 100</span>
        </div>
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        <p className="flex items-center gap-2 text-sm font-semibold text-content">
          <HeartPulse size={15} strokeWidth={2} style={{ color: style.color }} aria-hidden="true" />
          世界模型健康度
          <span
            className="rounded-full px-2 py-0.5 text-xs"
            style={{ color: style.color, background: "color-mix(in srgb, currentColor 15%, transparent)" }}
          >
            {style.label}
          </span>
        </p>
        {health.dimensions.map((dim) => {
          const dimRatio = dim.max > 0 ? dim.score / dim.max : 0;
          const dimColor = dimRatio >= 0.8 ? "var(--success)" : dimRatio >= 0.5 ? "var(--warning)" : "var(--danger)";
          return (
            <div key={dim.key} className="space-y-1">
              <div className="flex items-baseline justify-between gap-2 text-xs">
                <span className="text-secondary">{dim.label}</span>
                <span className="font-mono tabular-nums text-muted">
                  {dim.score}/{dim.max}
                </span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-base">
                <div
                  className="h-full rounded-full"
                  style={{
                    width: `${dimRatio * 100}%`,
                    background: dimColor,
                    transition: "width 0.8s cubic-bezier(0.16,1,0.3,1)",
                  }}
                />
              </div>
              <p className="text-[11px] text-muted">{dim.detail}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
}

const HealthGauge = memo(HealthGaugeInner);
export default HealthGauge;

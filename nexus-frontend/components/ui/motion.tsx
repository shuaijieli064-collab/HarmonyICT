"use client";

import { motion } from "framer-motion";
import type { ReactNode } from "react";

/** 统一入场动效原语：列表 stagger 逐项淡入。
 * 仅 transform/opacity（合成层）；reducedMotion="user" 由根部 MotionConfig 统一尊重系统偏好。
 * 父子必须在同一客户端组件树（framer-motion variants 约束）。
 */

export const listVariants = {
  hidden: {},
  show: {
    transition: { staggerChildren: 0.05, delayChildren: 0.05 },
  },
} as const;

export const itemVariants = {
  hidden: { opacity: 0, y: 8 },
  show: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.32, ease: [0.16, 1, 0.3, 1] as const },
  },
} as const;

/** 列表容器：visible 时触发子项 stagger */
export function MotionList({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.ul variants={listVariants} initial="hidden" animate="show" className={className}>
      {children}
    </motion.ul>
  );
}

/** 列表项：配合 MotionList 使用（li 语义） */
export function MotionItem({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.li variants={itemVariants} className={className}>
      {children}
    </motion.li>
  );
}

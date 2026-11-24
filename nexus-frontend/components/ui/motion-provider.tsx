"use client";

import { MotionConfig } from "framer-motion";
import type { ReactNode } from "react";

/** 全局动效配置：reducedMotion="user" 让所有 framer-motion 动画尊重系统「减弱动态」偏好。 */
export default function MotionProvider({ children }: { children: ReactNode }) {
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>;
}

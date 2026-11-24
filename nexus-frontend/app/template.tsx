"use client";

import { motion } from "framer-motion";
import type { ReactNode } from "react";

/** 路由级过渡：template.tsx 在每次导航时重新挂载，
 * 页面内容以淡入+上滑进入（仅 transform/opacity；reducedMotion 由根部 MotionConfig 尊重）。
 */
export default function Template({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
    >
      {children}
    </motion.div>
  );
}

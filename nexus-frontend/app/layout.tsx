import type { Metadata } from "next";
import SideNav from "@/components/layout/side-nav";
import MobileNav from "@/components/layout/mobile-nav";
import MotionProvider from "@/components/ui/motion-provider";
import "./globals.css";

export const metadata: Metadata = {
  title: "NEXUS · 个人资料智能管家",
  description: "上传资料，AI 抽取结构化事实，构建个人世界模型，主动发现风险，授权生成行动。",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="zh-CN" className="h-full antialiased" suppressHydrationWarning>
      <head>
        {/* 防主题闪烁：SSR 首帧前按 localStorage/系统偏好设置 data-theme */}
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var t=localStorage.getItem('nexus-theme');if(t!=='light'&&t!=='dark'){t=window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';}document.documentElement.setAttribute('data-theme',t);}catch(e){document.documentElement.setAttribute('data-theme','dark');}})();`,
          }}
        />
      </head>
      <body className="min-h-full">
        <MotionProvider>
          <div className="flex min-h-screen">
            <SideNav />
            <div className="flex min-w-0 flex-1 flex-col">
              <MobileNav />
              <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 lg:px-8">
                {children}
              </main>
            </div>
          </div>
        </MotionProvider>
      </body>
    </html>
  );
}

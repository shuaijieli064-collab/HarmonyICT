"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Sparkles } from "lucide-react";
import { login, register } from "@/services/auth";
import { ApiError } from "@/lib/http";

/** 登录/注册页（契约 2.1/2.2）：后端 H2 内存库重启即清空，每次联调可重新注册测试账号 */
export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (username.length < 3 || username.length > 32) {
      setError("用户名需 3-32 个字符");
      return;
    }
    if (password.length < 8 || password.length > 64) {
      setError("密码需 8-64 个字符");
      return;
    }
    setSubmitting(true);
    try {
      if (mode === "login") {
        await login(username, password);
      } else {
        await register({
          username,
          password,
          nickname: nickname.trim() || undefined,
          email: email.trim() || undefined,
        });
      }
      router.push("/");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "请求失败，请检查后端服务是否可用");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto mt-10 w-full max-w-sm space-y-6">
      <div className="text-center">
        <span className="icon-tile mx-auto mb-3 !h-12 !w-12" aria-hidden="true">
          <Sparkles size={22} strokeWidth={2} />
        </span>
        <p className="text-gradient text-2xl font-bold tracking-wide">NEXUS</p>
        <p className="mt-1 text-sm text-secondary">登录后开始构建你的个人世界模型</p>
      </div>

      <div className="flex rounded-lg border border-secondary/20 p-1 text-sm">
        {(["login", "register"] as const).map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => {
              setMode(item);
              setError(null);
            }}
            className={`flex-1 rounded-md px-3 py-1.5 transition-colors ${
              mode === item ? "bg-primary/15 text-primary" : "text-secondary hover:text-content"
            }`}
          >
            {item === "login" ? "登录" : "注册"}
          </button>
        ))}
      </div>

      <form onSubmit={onSubmit} className="space-y-4 rounded-xl border border-secondary/10 bg-card p-6">
        <div>
          <label htmlFor="username" className="mb-1 block text-xs text-secondary">用户名</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            className="glass-input w-full rounded-lg px-3 py-2 text-sm text-content"
            placeholder="3-32 个字符"
          />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-xs text-secondary">密码</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            className="glass-input w-full rounded-lg px-3 py-2 text-sm text-content"
            placeholder="8-64 个字符"
          />
        </div>
        {mode === "register" && (
          <>
            <div>
              <label htmlFor="nickname" className="mb-1 block text-xs text-secondary">昵称（可选）</label>
              <input
                id="nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                className="glass-input w-full rounded-lg px-3 py-2 text-sm text-content"
              />
            </div>
            <div>
              <label htmlFor="email" className="mb-1 block text-xs text-secondary">邮箱（可选）</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="glass-input w-full rounded-lg px-3 py-2 text-sm text-content"
              />
            </div>
          </>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="btn-glow w-full rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity disabled:opacity-60"
        >
          {submitting ? "提交中…" : mode === "login" ? "登录" : "注册并登录"}
        </button>
        <p className="text-center text-xs text-muted">
          <Link href="/" className="hover:text-secondary">先回驾驶舱看看 →</Link>
        </p>
      </form>
    </div>
  );
}
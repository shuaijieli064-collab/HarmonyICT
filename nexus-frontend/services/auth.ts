/* 认证服务（契约 docs/api-specification.md 2 认证模块）：
 * - 免认证接口仅 register/login/健康检查，其余一律带 Bearer token；
 * - 登录态持久化在 localStorage（H2 内存库后端重启即清空，测试账号每次联调重新注册）。
 */

import { request } from "@/lib/http";

/** 令牌失效相关错误码（10103 无效或过期 / 10104 已过期 / 10105 未登录或缺失） */
export const TOKEN_EXPIRED_CODES = new Set([10103, 10104, 10105]);

const TOKEN_KEY = "nexus_token";
const USER_KEY = "nexus_user";

/** 登录态变更事件：同 tab 内 useSyncExternalStore 订阅用（storage 事件仅跨 tab 触发） */
const AUTH_CHANGE_EVENT = "nexus-auth-change";

/** 订阅登录态变化（配合 useSyncExternalStore；server 端返回空订阅） */
export function subscribeAuth(onChange: () => void): () => void {
  if (typeof window === "undefined") return () => undefined;
  window.addEventListener(AUTH_CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(AUTH_CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** 登录态快照（token 字符串，值稳定可作 useSyncExternalStore snapshot） */
export function getAuthSnapshot(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

/** 当前用户名快照 */
export function getUsernameSnapshot(): string | null {
  if (typeof window === "undefined") return null;
  return getAuthUser()?.username ?? null;
}

function notifyAuthChange(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(AUTH_CHANGE_EVENT));
}

/** 认证响应（契约 2.1/2.2）：userId 为雪花数字字符串 */
export interface AuthUser {
  token: string;
  userId: string;
  username: string;
  nickname: string | null;
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function getAuthUser(): AuthUser | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function isLoggedIn(): boolean {
  return getToken() !== null;
}

/** 清除本地登录态（logout 或令牌失效时调用） */
export function clearAuth(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
  notifyAuthChange();
}

function saveAuth(user: AuthUser): void {
  window.localStorage.setItem(TOKEN_KEY, user.token);
  window.localStorage.setItem(USER_KEY, JSON.stringify(user));
  notifyAuthChange();
}

export interface RegisterInput {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
}

/** 注册（契约 2.1）：成功即返回 token，注册后无需再登录 */
export async function register(input: RegisterInput): Promise<AuthUser> {
  const user = await request<AuthUser>("/api/v1/auth/register", { method: "POST", body: input });
  saveAuth(user);
  return user;
}

/** 登录（契约 2.2）：失败返回 code=10101 用户名或密码错误 */
export async function login(username: string, password: string): Promise<AuthUser> {
  const user = await request<AuthUser>("/api/v1/auth/login", {
    method: "POST",
    body: { username, password },
  });
  saveAuth(user);
  return user;
}

/** 退出（契约 2.3）：服务端将 token 加入黑名单，本地同步清除 */
export async function logout(): Promise<void> {
  try {
    await request<null>("/api/v1/auth/logout", { method: "POST" });
  } finally {
    clearAuth();
  }
}

/** 当前用户资料（契约 3：GET /users/me） */
export interface UserProfile {
  userId: string;
  username: string;
  nickname: string | null;
  email: string | null;
  createdAt: string;
}

export function getProfile(): Promise<UserProfile> {
  return request<UserProfile>("/api/v1/users/me");
}
/* HTTP 请求统一封装（契约 docs/api-specification.md 1 通用约定）：
 * - 所有请求走 Result<T> 包裹解析，code !== 0 一律抛 ApiError；
 * - 认证令牌自动注入 Authorization: Bearer <token>（auth 服务提供）；
 * - 令牌失效（10103/10104/10105）清除本地登录态并引导重新登录。
 */

import { getToken, clearAuth, TOKEN_EXPIRED_CODES } from "@/services/auth";

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** 业务错误：code 与 message 来自 Result 包裹 */
export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

/** 认证失效：调用方据此引导用户重新登录 */
export class AuthExpiredError extends ApiError {
  constructor(code: number, message: string) {
    super(code, message);
    this.name = "AuthExpiredError";
  }
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

type QueryValue = string | number | boolean | null | undefined;
type QueryMap = Record<string, QueryValue>;

interface RequestOptions {
  method?: Method;
  /** JSON 请求体（multipart 上传请传 rawBody） */
  body?: unknown;
  /** 原样发送的请求体（如 FormData），不设 Content-Type */
  rawBody?: BodyInit;
  /** 额外 query 参数，undefined/null 的键会被丢弃 */
  query?: object;
}

function buildUrl(path: string, query?: object): string {
  const url = new URL(path, API_BASE_URL);
  if (query) {
    for (const [key, value] of Object.entries(query as QueryMap)) {
      if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

interface ResultEnvelope<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

/** 统一请求入口：services/ 各模块只通过本函数访问后端 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body: options.rawBody ?? (options.body !== undefined ? JSON.stringify(options.body) : undefined),
    });
  } catch {
    throw new ApiError(-1, "网络异常，请检查后端服务是否可用");
  }

  let envelope: ResultEnvelope<T>;
  try {
    envelope = (await response.json()) as ResultEnvelope<T>;
  } catch {
    throw new ApiError(-1, `响应解析失败（HTTP ${response.status}）`);
  }

  if (envelope.code === 0) return envelope.data;
  if (TOKEN_EXPIRED_CODES.has(envelope.code)) {
    clearAuth();
    throw new AuthExpiredError(envelope.code, envelope.message);
  }
  throw new ApiError(envelope.code, envelope.message);
}

/** 端到端演示探活：GET /actuator/health（免认证，C 联调用） */
export async function checkBackendHealth(): Promise<boolean> {
  try {
    const response = await fetch(new URL("/actuator/health", API_BASE_URL));
    const payload = (await response.json()) as { status?: string };
    return payload.status === "UP";
  } catch {
    return false;
  }
}
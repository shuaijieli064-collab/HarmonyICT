"use client";

import { useSyncExternalStore } from "react";
import { getAuthSnapshot, getUsernameSnapshot, subscribeAuth } from "@/services/auth";

/** 登录态 hook（useSyncExternalStore 订阅 localStorage，SSR 安全） */
export function useIsLoggedIn(): boolean {
  return useSyncExternalStore(subscribeAuth, getAuthSnapshot, () => null) !== null;
}

/** 当前登录用户名 hook */
export function useUsername(): string | null {
  return useSyncExternalStore(subscribeAuth, getUsernameSnapshot, () => null);
}
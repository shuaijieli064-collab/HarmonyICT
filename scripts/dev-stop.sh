#!/usr/bin/env bash
# ============================================================
# NEXUS 本地一键停止（按 scripts/.dev/*.pid 结束前后端）
# 用法：bash scripts/dev-stop.sh
# ============================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_DIR="$ROOT/scripts/.dev"

stop_one() {
  local name="$1" pid_file="$DEV_DIR/$1.pid"
  if [[ ! -f "$pid_file" ]]; then
    echo "[$name] 无 PID 文件，跳过"
    return
  fi
  local pid
  pid="$(cat "$pid_file")"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    echo "[$name] 进程 $pid 已不存在"
    rm -f "$pid_file"
    return
  fi
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.2
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
    echo "[$name] 强制结束 $pid"
  else
    echo "[$name] 已结束 $pid"
  fi
  rm -f "$pid_file"
}

stop_one backend
stop_one frontend
echo "完成。日志保留在 $DEV_DIR/"

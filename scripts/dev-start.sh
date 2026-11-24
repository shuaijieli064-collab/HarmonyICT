#!/usr/bin/env bash
# ============================================================
# NEXUS 本地一键启动（后端 local+mock + 前端 dev）
# 用法：bash scripts/dev-start.sh [--skip-build]
# 说明：
#   - 后端默认 local profile（H2 内存库 + mock 抽取），无需华为云凭据
#   - 日志与 PID 写入 scripts/.dev/（已 gitignore）
#   - 停止用 bash scripts/dev-stop.sh
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_DIR="$ROOT/scripts/.dev"
JAR="$ROOT/nexus-backend/target/nexus-backend-0.1.0-SNAPSHOT.jar"
MVN="${MVN_CMD:-D:/hong_meng/tools/apache-maven-3.9.9/bin/mvn.cmd}"
BACKEND_URL="http://localhost:8080/actuator/health"
FRONTEND_URL="http://localhost:3000"

mkdir -p "$DEV_DIR"

port_in_use() {
  (echo > "/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

if port_in_use 8080; then
  echo "[ERROR] 端口 8080 已被占用。请先运行 dev-stop.sh 或手动结束占用进程。"
  exit 1
fi
if port_in_use 3000; then
  echo "[ERROR] 端口 3000 已被占用。请先运行 dev-stop.sh 或手动结束占用进程。"
  exit 1
fi

if [[ "${1:-}" != "--skip-build" ]]; then
  echo "[1/4] 构建后端（跳过请加 --skip-build）..."
  (cd "$ROOT/nexus-backend" && "$MVN" -q clean package)
else
  echo "[1/4] 跳过构建；检查 jar 是否存在..."
  [[ -f "$JAR" ]] || { echo "[ERROR] 未找到 $JAR，请先不带 --skip-build 运行一次。"; exit 1; }
fi

echo "[2/4] 启动后端（local + mock）..."
(cd "$ROOT/nexus-backend" && java -jar "$JAR") >"$DEV_DIR/backend.log" 2>&1 &
echo $! > "$DEV_DIR/backend.pid"

echo "[3/4] 等待后端健康检查..."
for i in $(seq 1 60); do
  if curl -fsS "$BACKEND_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "       后端已就绪：$BACKEND_URL"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo "[ERROR] 后端 30 秒内未就绪，请查看 $DEV_DIR/backend.log"
    exit 1
  fi
  sleep 0.5
done

echo "[4/4] 启动前端（next dev）..."
(cd "$ROOT/nexus-frontend" && npm run dev) >"$DEV_DIR/frontend.log" 2>&1 &
echo $! > "$DEV_DIR/frontend.pid"

echo ""
echo "==== NEXUS 本地环境已启动 ===="
echo "前端：$FRONTEND_URL"
echo "后端：$BACKEND_URL"
echo "日志：$DEV_DIR/{backend,frontend}.log"
echo "停止：bash scripts/dev-stop.sh"

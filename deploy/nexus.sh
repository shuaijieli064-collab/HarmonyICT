#!/usr/bin/env bash
# NEXUS ECS 启停脚本（配合 systemd；CodeArts 部署任务调用 restart/status）
# 用法：./nexus.sh {start|stop|restart|status|health}
set -euo pipefail

SERVICE=nexus-backend
HEALTH_URL="http://127.0.0.1:8080/actuator/health"

case "${1:-}" in
  start)
    sudo systemctl start "$SERVICE"
    echo "[nexus] $SERVICE started"
    ;;
  stop)
    sudo systemctl stop "$SERVICE"
    echo "[nexus] $SERVICE stopped"
    ;;
  restart)
    sudo systemctl restart "$SERVICE"
    echo "[nexus] $SERVICE restarted"
    ;;
  status)
    systemctl status "$SERVICE" --no-pager
    ;;
  health)
    # 部署任务的健康新检查：UP 即通过（redis/obs 弱依赖不阻塞）
    for i in $(seq 1 30); do
      if curl -fsS "$HEALTH_URL" | grep -q '"status":"UP"'; then
        echo "[nexus] health check passed"
        exit 0
      fi
      sleep 2
    done
    echo "[nexus] health check FAILED after 60s" >&2
    exit 1
    ;;
  *)
    echo "用法: $0 {start|stop|restart|status|health}" >&2
    exit 1
    ;;
esac

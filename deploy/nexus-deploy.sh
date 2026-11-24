#!/usr/bin/env bash
# ============================================================
# NEXUS 华为云 ECS 部署脚本（成员 A）
# 用法：
#   ./nexus-deploy.sh install    首次安装 systemd unit 与 nginx 站点（需 sudo）
#   ./nexus-deploy.sh upload     上传构建产物：后端 jar + 前端目录到 /opt/nexus
#   ./nexus-deploy.sh start|stop|restart|status   服务生命周期
#   ./nexus-deploy.sh health     逐项健康检查（后端 /actuator/health、前端、端口）
# 约定：
#   - 凭据文件 /etc/nexus/nexus.env（权限 600）由运维在 ECS 上手填，脚本只引用不读取内容
#   - 本脚本不含任何密钥，执行时不回显环境变量值
#   - 对应方案：docs/cloud-deployment-plan.md 第 6 节
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR=/opt/nexus
FRONT_DIR="$APP_DIR/nexus-frontend"
JAR_NAME=nexus-backend.jar
HEALTH_URL=http://127.0.0.1:8080/actuator/health

log() { printf '[nexus] %s\n' "$*"; }
die() { printf '[nexus][错误] %s\n' "$*" >&2; exit 1; }

need_root() {
  [[ "$(id -u)" -eq 0 ]] || die "该操作需要 root，请用 sudo 执行"
}

install_unit() {
  need_root
  log "安装 systemd unit 与 nginx 站点"
  install -d -m 755 "$APP_DIR"
  install -d -m 700 /etc/nexus
  cp "$SCRIPT_DIR/nexus-backend.service" /etc/systemd/system/nexus-backend.service
  cp "$SCRIPT_DIR/nexus-frontend.service" /etc/systemd/system/nexus-frontend.service
  if [[ -d /etc/nginx/sites-available ]]; then
    cp "$SCRIPT_DIR/nginx-nexus.conf" /etc/nginx/sites-available/nexus
    ln -sfn /etc/nginx/sites-available/nexus /etc/nginx/sites-enabled/nexus
    nginx -t && systemctl reload nginx
  else
    log "未检测到 /etc/nginx/sites-available，请手动放置 nginx-nexus.conf 后 reload"
  fi
  systemctl daemon-reload
  log "unit 安装完成；请先创建 /etc/nexus/nexus.env（权限 600）再 start"
}

upload_artifacts() {
  local jar_src="${1:-}" front_src="${2:-}"
  [[ -f "$jar_src" ]] || die "未找到后端 jar，用法：$0 upload <backend.jar> <frontend目录>"
  [[ -d "$front_src" ]] || die "未找到前端构建目录，用法：$0 upload <backend.jar> <frontend目录>"
  need_root
  install -d -m 755 "$APP_DIR"
  # 备份旧包，保留最近一份，便于回滚
  if [[ -f "$APP_DIR/$JAR_NAME" ]]; then
    cp -f "$APP_DIR/$JAR_NAME" "$APP_DIR/$JAR_NAME.bak"
    log "已备份旧 jar 到 $APP_DIR/$JAR_NAME.bak"
  fi
  cp -f "$jar_src" "$APP_DIR/$JAR_NAME"
  rm -rf "$FRONT_DIR"
  mkdir -p "$FRONT_DIR"
  cp -a "$front_src/." "$FRONT_DIR/"
  chown -R nexus:nexus "$APP_DIR"
  log "产物上传完成：jar=$APP_DIR/$JAR_NAME front=$FRONT_DIR"
}

health_check() {
  local ok=0
  log "检查后端 $HEALTH_URL"
  if command -v curl >/dev/null 2>&1 && [[ "$(curl -fsS "$HEALTH_URL" 2>/dev/null)" == *'"status":"UP"'* ]]; then
    log "后端健康：UP"
  else
    printf '[nexus][告警] 后端未就绪，查看：journalctl -u nexus-backend -n 100\n' >&2
    ok=1
  fi
  log "检查前端 :3000"
  if curl -fsS -o /dev/null http://127.0.0.1:3000; then
    log "前端可访问"
  else
    printf '[nexus][告警] 前端未就绪，查看：journalctl -u nexus-frontend -n 100\n' >&2
    ok=1
  fi
  return "$ok"
}

case "${1:-}" in
  install)  install_unit ;;
  upload)   upload_artifacts "${2:-}" "${3:-}" ;;
  start)    systemctl start nexus-backend nexus-frontend && log "已启动" ;;
  stop)     systemctl stop nexus-frontend nexus-backend && log "已停止" ;;
  restart)  systemctl restart nexus-backend nexus-frontend && log "已重启" ;;
  status)   systemctl status nexus-backend nexus-frontend --no-pager ;;
  health)   health_check ;;
  -h|--help|"")
    sed -n '1,16p' "$0"
    ;;
  *) die "未知参数：$1" ;;
esac

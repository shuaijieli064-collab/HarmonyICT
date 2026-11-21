#!/usr/bin/env bash
# ============================================================
# 真实 MaaS / OCR 调用验收脚本（成员 B）
# 用法：
#   ./verify-real-call.sh [env文件]        默认读取 real-call.env
#   ./verify-real-call.sh -h               查看帮助
# 说明：
#   - 凭据只从 env 文件/系统环境变量读取，脚本本身不含任何密钥
#   - env 文件被 .gitignore 忽略（real-call.env）
#   - 分两项验收：① MaaS 文本抽取 ② OCR 图片识别
#   - 脚本会校验必填项缺一即退出，避免用空凭据空跑
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/../nexus-backend"
ENV_FILE="${1:-$SCRIPT_DIR/real-call.env}"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  echo "用法: ./verify-real-call.sh [env文件]"
  echo "  env 文件默认 ./real-call.env（复制 real-call.env.template 并填值）"
  echo "  或直接依赖已导出的系统环境变量（不传 env 文件则仍尝试读取默认文件）"
  exit 0
fi

# 载入 env 文件（如存在）：忽略注释与空行，仅解析 KEY=VALUE
if [[ -f "$ENV_FILE" ]]; then
  echo "[1/4] 载入凭据文件: $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
else
  echo "[1/4] 未找到 $ENV_FILE，改用已导出的系统环境变量"
fi

# 必填项校验
REQUIRED=(
  NEXUS_AI_MAAS_API_KEY
  NEXUS_AI_MAAS_MODEL
  NEXUS_AI_OCR_USERNAME
  NEXUS_AI_OCR_PASSWORD
  NEXUS_AI_OCR_DOMAIN_NAME
  NEXUS_AI_OCR_PROJECT_ID
)
MISSING=()
for key in "${REQUIRED[@]}"; do
  val="${!key:-}"
  if [[ -z "$val" || "$val" == \<* ]]; then
    MISSING+=("$key")
  fi
done
if (( ${#MISSING[@]} > 0 )); then
  echo "[错误] 以下必填凭据缺失或仍为占位符：" >&2
  printf '  - %s\n' "${MISSING[@]}" >&2
  echo "请复制 real-call.env.template 为 real-call.env 并填入真实值。" >&2
  exit 1
fi
echo "[2/4] 凭据校验通过（值不回显）"

run_demo() {
  local label="$1" input="$2"
  echo "---- $label ----"
  local out
  out=$(cd "$BACKEND_DIR" && timeout 180 mvn -q spring-boot:run \
    -Dspring-boot.run.arguments="--nexus.ai.mode=real --nexus.ai.demo.enabled=true --nexus.ai.demo.input=$input" 2>&1) || true
  # 抽取 Demo 完成日志（Spring Boot 常驻服务被 timeout 终止属正常）
  echo "$out" | grep "抽取 Demo 完成" || { echo "$out" | tail -20; echo "[错误] $label 未输出完成日志" >&2; return 1; }
}

echo "[3/4] 验收① MaaS 文本抽取（subscription.txt）"
run_demo "MaaS 文本抽取" "../nexus-ai/testdata/subscription.txt"

echo "[4/4] 验收② OCR 图片识别 + MaaS 抽取（study-plan.png）"
run_demo "OCR 图片识别" "../nexus-ai/testdata/study-plan.png"

echo "==== 验收完成，核对输出 ===="
echo "输出文件: $SCRIPT_DIR/testdata/extraction-result.json"
echo "检查项: provider=huawei-llm/huawei-ocr; entity/fact/event 均带 sourceId; 置信度与证据片段合理"

#!/usr/bin/env bash
# 校园代拿微服务 · Linux 一键启停（需先 mvn package 并将 jar 放到 JAR_DIR）
# 用法：./start-services.sh start | stop | status
# 依赖：本机 Nacos(8848)、RabbitMQ(5672) 已就绪（可用 deploy/docker-compose.yml）

set -euo pipefail

JAR_DIR="${JAR_DIR:-/opt/campus/services}"
LOG_DIR="${LOG_DIR:-/opt/campus/logs}"
ENV_FILE="${ENV_FILE:-/opt/campus/aliyun.env}"
PROD_CONFIG="${PROD_CONFIG:-/opt/campus/application-prod.yml}"
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
JAVA="${JAVA:-java}"
# 小内存机器（<=4G）建议限制堆，避免 7 个服务同时启动 OOM
JAVA_MEM_OPTS="${JAVA_MEM_OPTS:--Xms128m -Xmx256m -XX:+UseSerialGC}"

mkdir -p "$LOG_DIR"

load_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  fi
  export NACOS_ADDR
}

start_one() {
  local name="$1"
  local jar="$2"
  local log="$LOG_DIR/${name}.log"
  if [[ ! -f "$jar" ]]; then
    echo "[skip] $name: jar not found: $jar"
    return 1
  fi
  if pgrep -f "$jar" >/dev/null 2>&1; then
    echo "[ok]   $name already running"
    return 0
  fi
  local -a extra_args=()
  if [[ -f "$PROD_CONFIG" ]]; then
    extra_args=(--spring.config.additional-location="file:${PROD_CONFIG}")
  fi
  nohup "$JAVA" $JAVA_MEM_OPTS -jar "$jar" "${extra_args[@]}" >>"$log" 2>&1 &
  echo "[start] $name -> $log"
}

stop_one() {
  local jar="$1"
  pkill -f "$jar" 2>/dev/null || true
}

status_one() {
  local name="$1"
  local jar="$2"
  if pgrep -f "$jar" >/dev/null 2>&1; then
    echo "[run]  $name"
  else
    echo "[stop] $name"
  fi
}

JARS=(
  "campus-auth-service:auth"
  "campus-user-service:user"
  "campus-wallet-service:wallet"
  "campus-audit-service:audit"
  "campus-chat-service:chat"
  "campus-task-service:task"
  "campus-gateway:gateway"
)

resolve_jar() {
  local prefix="$1"
  local f
  f=$(ls "$JAR_DIR/${prefix}"-*.jar 2>/dev/null | head -n1 || true)
  if [[ -z "$f" ]]; then
    f="$JAR_DIR/${prefix}.jar"
  fi
  echo "$f"
}

cmd="${1:-start}"
load_env

case "$cmd" in
  start)
    start_one "auth" "$(resolve_jar campus-auth-service)" || true
    sleep 15
    for entry in "campus-user-service:user" "campus-wallet-service:wallet" "campus-audit-service:audit" "campus-chat-service:chat"; do
      IFS=: read -r prefix short <<<"$entry"
      start_one "$short" "$(resolve_jar "$prefix")" || true
      sleep 12
    done
    start_one "task" "$(resolve_jar campus-task-service)" || true
    sleep 15
    start_one "gateway" "$(resolve_jar campus-gateway)" || true
    echo "Gateway: http://127.0.0.1:8080"
    echo "提示：小内存机器请等待 90~120 秒后再执行 status"
    ;;
  stop)
    for entry in "${JARS[@]}"; do
      IFS=: read -r prefix _ <<<"$entry"
      stop_one "$(resolve_jar "$prefix")"
    done
    echo "All services stopped."
    ;;
  status)
    for entry in "${JARS[@]}"; do
      IFS=: read -r prefix short <<<"$entry"
      status_one "$short" "$(resolve_jar "$prefix")"
    done
    ;;
  *)
    echo "Usage: $0 start|stop|status"
    exit 1
    ;;
esac

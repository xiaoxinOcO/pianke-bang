#!/usr/bin/env bash
set -euo pipefail

JAR="/opt/campus/services/campus-chat-service-1.0.0-SNAPSHOT.jar"
LOG="/opt/campus/logs/chat.log"
ENV_FILE="/opt/campus/aliyun.env"
PROD_CONFIG="/opt/campus/application-prod.yml"

pkill -f "$JAR" 2>/dev/null || true
sleep 2

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi
export NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"

mkdir -p /opt/campus/logs
nohup java -Xms128m -Xmx256m -XX:+UseSerialGC -jar "$JAR" \
  --spring.config.additional-location="file:${PROD_CONFIG}" \
  >>"$LOG" 2>&1 &

sleep 6
if pgrep -f "$JAR" >/dev/null 2>&1; then
  echo "[ok] chat started"
  pgrep -af "$JAR"
else
  echo "[fail] chat not running"
  tail -n 40 "$LOG" || true
  exit 1
fi

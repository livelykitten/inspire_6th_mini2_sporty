#!/usr/bin/env bash
# L6 장애 주입 부하 (이슈 #44): 평균 부하(L2)를 거는 동안 facility-service 를 멈췄다가 다시 올려
# "시설에 의존하지 않는 매치 목록이 계속 응답하는가 · 재기동 뒤 회복은 얼마나 걸리는가" 를 본다.
#
#   bash tests/load/chaos.sh                       # 기본: 평균 부하 5분 중 120초 시점에 중지, 180초 뒤 재기동
#   VUS=10 DURATION=3m STOP_AT=60 DOWN_FOR=60 bash tests/load/chaos.sh
#
# 결과: tests/load/results/chaos.json (k6), chaos-recovery.txt (재기동 뒤 시설 API 복구 초)
# 담당: 반정욱 (@salieri009) · 작업 위치: tests/load/chaos.sh
set -u
cd "$(dirname "$0")/../.."
COMPOSE_CMD="${COMPOSE_CMD:-docker compose -f compose.yaml -f compose.app.yaml}"
BASE_URL="${BASE_URL:-http://localhost:8000}"
VUS="${VUS:-20}"
DURATION="${DURATION:-5m}"
STOP_AT="${STOP_AT:-120}"
DOWN_FOR="${DOWN_FOR:-180}"
OUT_DIR="${OUT_DIR:-tests/load/results}"
mkdir -p "$OUT_DIR"

echo "[chaos] 평균 부하 시작 (VUS=$VUS DURATION=$DURATION) → ${STOP_AT}s 뒤 facility-service 중지, ${DOWN_FOR}s 뒤 재기동"
k6 run --quiet tests/load/k6/scenarios.js -e SCENARIO=chaos -e BASE_URL="$BASE_URL" -e VUS="$VUS" -e DURATION="$DURATION" -e OUT_DIR="$OUT_DIR" &
K6_PID=$!

sleep "$STOP_AT"
echo "[chaos] facility-service 중지"
$COMPOSE_CMD stop facility-service >/dev/null 2>&1
sleep "$DOWN_FOR"
echo "[chaos] facility-service 재기동"
t0=$(date +%s)
$COMPOSE_CMD start facility-service >/dev/null 2>&1

# 재기동 뒤 게이트웨이 경유 시설 API 가 다시 200 이 되는 시간
secs=-1
for i in $(seq 1 90); do
  code="$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$BASE_URL/api/services" || echo 000)"
  if [ "$code" = "200" ]; then secs=$(( $(date +%s) - t0 )); break; fi
  sleep 2
done
echo "$secs" > "$OUT_DIR/chaos-recovery.txt"
echo "[chaos] 재기동 뒤 시설 API 복구: ${secs}s (-1 이면 복구 실패)"

wait "$K6_PID"
echo "[chaos] 완료"

#!/usr/bin/env bash
# 배포 확인(smoke). EC2 안(기본값)에서도, 본인 PC 에서 ALB 주소로도 쓴다.
#
#   bash deploy/ec2/smoke.sh                         # http://127.0.0.1 (EC2 안, nginx 80)
#   bash deploy/ec2/smoke.sh http://<ALB 주소>       # 배포 주소 확인
#   TRIES=60 WAIT=5 bash deploy/ec2/smoke.sh ...     # 재시도 횟수 · 간격(초)
#
# 기동 직후 30~60초는 Eureka 레지스트리 캐시 때문에 게이트웨이가 503 을 줄 수 있어 각 항목을 재시도한다.
# 가입 요청이 있어 smoke{시각}@example.com 계정이 DB 에 하나 남는다 (시연 데이터와 구분된다).
#
# 담당: 반정욱 (@salieri009) · 작업 위치: deploy/ec2/smoke.sh
set -uo pipefail

BASE="${1:-http://127.0.0.1}"
BASE="${BASE%/}"
TRIES="${TRIES:-30}"
WAIT="${WAIT:-10}"
fail=0

code() { curl -s -o /dev/null -m 15 -w '%{http_code}' "$@" 2>/dev/null || echo 000; }

# expect "설명" 기대코드 명령...   기대 코드가 나올 때까지 재시도
expect() {
  local name="$1" want="$2" got="" i
  shift 2
  for i in $(seq 1 "$TRIES"); do
    got="$("$@")"
    if [ "$got" = "$want" ]; then
      printf '  PASS %-42s %s\n' "$name" "$got"
      return 0
    fi
    sleep "$WAIT"
  done
  printf '  FAIL %-42s 기대 %s 실제 %s\n' "$name" "$want" "$got"
  fail=1
  return 1
}

echo "smoke: $BASE (재시도 ${TRIES}회 x ${WAIT}초)"
expect "nginx /healthz" 200 code "$BASE/healthz"
expect "SPA /" 200 code "$BASE/"
expect "GET /api/services (gateway -> facility)" 200 code "$BASE/api/services"
expect "GET /api/matches (gateway -> match)" 200 code "$BASE/api/matches"
expect "토큰 없이 GET /api/users/me" 401 code "$BASE/api/users/me"

stamp="$(date +%s)"
email="smoke${stamp}@example.com"
nick="smk${stamp: -6}"
pw='Passw0rd!'
signup_body="{\"email\":\"$email\",\"password\":\"$pw\",\"gender\":\"MALE\",\"nickname\":\"$nick\",\"district\":\"GANGNAM\",\"sportTypes\":[\"FUTSAL\"]}"
expect "POST /api/users (가입, gateway -> user)" 201 code -X POST "$BASE/api/users" -H 'Content-Type: application/json' -d "$signup_body"

token=""
for i in $(seq 1 "$TRIES"); do
  token="$(curl -s -m 15 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pw\"}" 2>/dev/null \
    | sed -n 's/.*"accessToken" *: *"\([^"]*\)".*/\1/p')"
  [ -n "$token" ] && break
  sleep "$WAIT"
done
if [ -n "$token" ]; then
  printf '  PASS %-42s %s\n' "POST /api/auth/login (accessToken)" "발급"
  expect "토큰으로 GET /api/users/me" 200 code "$BASE/api/users/me" -H "Authorization: Bearer $token"
  # AI 는 OpenAI 키가 없으면 500 이 정상이다. 404/503 이 아니면 gateway -> ai 라우트는 살아 있다.
  ai="$(code "$BASE/api/ai/matches/recommendations" -H "Authorization: Bearer $token")"
  if [ "$ai" = "200" ] || [ "$ai" = "500" ]; then
    printf '  PASS %-42s %s\n' "AI 라우트 도달 (200/500)" "$ai"
  else
    printf '  FAIL %-42s 기대 200|500 실제 %s\n' "AI 라우트 도달 (200/500)" "$ai"
    fail=1
  fi
else
  printf '  FAIL %-42s %s\n' "POST /api/auth/login (accessToken)" "토큰 없음"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "smoke OK"
else
  echo "smoke FAILED" >&2
  exit 1
fi

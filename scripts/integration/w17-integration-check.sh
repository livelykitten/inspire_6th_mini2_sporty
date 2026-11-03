#!/usr/bin/env bash
set -euo pipefail

# Git Bash / local W-17 integration smoke test.
# Requires running user-service (:8001), match-service (:8002), Eureka, DB, Redis.
USER_URL="${USER_URL:-http://localhost:8001}"
MATCH_URL="${MATCH_URL:-http://localhost:8002}"

nonce="$(date +%s)-$RANDOM"
email="w17-${nonce}@example.com"
nickname="w17_${nonce}"
password='W17testpass123!'

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

signup_json="$(printf '{"email":"%s","password":"%s","gender":"MALE","nickname":"%s","district":"GANGNAM","sportTypes":["SOCCER"]}' "$email" "$password" "$nickname")"
login_json="$(printf '{"email":"%s","password":"%s"}' "$email" "$password")"

printf '== 1. 회원가입: %s ==\n' "$email"
status="$(curl.exe --silent --show-error --max-time 10 --output "$tmp/signup.json" \
  --write-out '%{http_code}' --request POST "$USER_URL/api/users" \
  --header 'Content-Type: application/json' --data-raw "$signup_json")"
if [[ "$status" != '201' ]]; then
  printf '회원가입 실패 (HTTP %s):\n' "$status"
  cat "$tmp/signup.json"; printf '\n'
  exit 1
fi
printf '회원가입 성공 (201)\n'

printf '\n== 2. 로그인하여 userId 확인 ==\n'
status="$(curl.exe --silent --show-error --max-time 10 --output "$tmp/login.json" \
  --write-out '%{http_code}' --request POST "$USER_URL/api/auth/login" \
  --header 'Content-Type: application/json' --data-raw "$login_json")"
if [[ "$status" != '200' ]]; then
  printf '로그인 실패 (HTTP %s). Redis 실행 여부를 확인하세요.\n' "$status"
  cat "$tmp/login.json"; printf '\n'
  exit 1
fi
# Token is intentionally not printed.
user_id="$(sed -nE 's/.*"userId"[[:space:]]*:[[:space:]]*([0-9]+).*/\1/p' "$tmp/login.json")"
if [[ -z "$user_id" ]]; then
  printf '로그인 응답에서 userId를 찾지 못했습니다.\n'
  exit 1
fi
printf 'userId=%s\n' "$user_id"

printf '\n== 3. IN-01: 회원 상태 조회 ==\n'
status="$(curl.exe --silent --show-error --max-time 10 --output "$tmp/status.json" \
  --write-out '%{http_code}' "$USER_URL/internal/users/$user_id/status")"
cat "$tmp/status.json"; printf '\n'
[[ "$status" == '200' ]] || { printf 'IN-01 실패 (HTTP %s)\n' "$status"; exit 1; }

printf '\n== 4. IN-02: 회원 프로필 조회 ==\n'
status="$(curl.exe --silent --show-error --max-time 10 --output "$tmp/profiles.json" \
  --write-out '%{http_code}' "$USER_URL/internal/users/profiles?userIds=$user_id")"
cat "$tmp/profiles.json"; printf '\n'
[[ "$status" == '200' ]] || { printf 'IN-02 실패 (HTTP %s)\n' "$status"; exit 1; }

printf '\n== 5. EM-08: match-service -> Feign -> user-service ==\n'
status="$(curl.exe --silent --show-error --max-time 10 --output "$tmp/matches.json" \
  --write-out '%{http_code}' --header "X-User-Id: $user_id" \
  "$MATCH_URL/api/matches/me")"
cat "$tmp/matches.json"; printf '\n'
[[ "$status" == '200' ]] || { printf 'EM-08 실패 (HTTP %s)\n' "$status"; exit 1; }

printf '\n완료: 회원가입, 로그인, IN-01, IN-02, EM-08 모두 HTTP 상태 확인 성공\n'
printf 'Test user: %s (userId=%s)\n' "$email" "$user_id"
printf '\n장애 테스트: user-service 프로세스를 중지한 뒤 다음을 실행하세요.\n'
printf 'curl.exe -i -H "X-User-Id: %s" "%s/api/matches/me"\n' "$user_id" "$MATCH_URL"
printf '기대 응답: HTTP 503, USER_SERVICE_UNAVAILABLE\n'

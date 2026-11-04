#!/usr/bin/env bash
# W-13 확인 시나리오 중 curl 로 확인되는 부분 (게이트웨이 :8000 경유). 먼저 scripts/w13-seed.sh 를 실행해 둔다.
GW=${GW:-http://localhost:8000}; PW='Passw0rd!'
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
check() { printf '%-52s 기대 %-8s 실제 %s %s\n' "$1" "$2" "$3" "$([ "$2" = "$3" ] && echo PASS || echo FAIL)"; }
LOGIN=$(curl -s -X POST $GW/api/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"w13a@example.com\",\"password\":\"$PW\"}")
TOKEN=$(echo "$LOGIN" | python -c 'import sys,json;print(json.load(sys.stdin).get("accessToken",""))')
REFRESH=$(echo "$LOGIN" | python -c 'import sys,json;print(json.load(sys.stdin).get("refreshToken",""))')
check "로그인 (accessToken 발급)" yes "$([ -n "$TOKEN" ] && echo yes || echo no)"
check "토큰 없이 GET /api/users/me" 401 "$(code $GW/api/users/me)"
check "토큰으로 GET /api/users/me" 200 "$(code $GW/api/users/me -H "Authorization: Bearer $TOKEN")"
check "X-User-Id 만 (999) GET /api/users/me" 401 "$(code $GW/api/users/me -H 'X-User-Id: 999')"
check "토큰 + X-User-Id 999 → 토큰 sub 로 덮어씀" 200 "$(code $GW/api/users/me -H "Authorization: Bearer $TOKEN" -H 'X-User-Id: 999')"
check "GET /api/matches (비로그인)" 200 "$(code $GW/api/matches)"
MID=$(curl -s $GW/api/matches | python -c 'import sys,json;d=json.load(sys.stdin);l=d if isinstance(d,list) else (d.get("content") or d.get("data") or []);print(l[0]["matchId"] if l else 1)' 2>/dev/null)
check "GET /api/matches/$MID 상세 (비로그인)" 200 "$(code $GW/api/matches/$MID)"
check "틀린 토큰 + 매치 상세 (비로그인 처리)" 200 "$(code $GW/api/matches/$MID -H 'Authorization: Bearer wrong')"
check "없는 매치 상세" 404 "$(code $GW/api/matches/999999)"
check "토큰 없이 GET /api/matches/me" 401 "$(code $GW/api/matches/me)"
check "토큰으로 GET /api/matches/me" 200 "$(code $GW/api/matches/me -H "Authorization: Bearer $TOKEN")"
check "GET /api/services (비로그인)" 200 "$(code $GW/api/services)"
check "GET /api/services/1 상세" 200 "$(code $GW/api/services/1)"
check "POST /api/matches 토큰 없이" 401 "$(code -X POST $GW/api/matches -H 'Content-Type: application/json' -d '{}')"
check "리프레시 토큰으로 보호 경로 접근" 401 "$(code $GW/api/users/me -H "Authorization: Bearer $REFRESH")"
check "POST /api/auth/refresh" 200 "$(code -X POST $GW/api/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}")"
check "GET /internal/members/1 (외부 차단)" 404 "$(code $GW/internal/members/1)"
echo "--- CORS (Origin: http://localhost:3000)"
curl -s -i -X OPTIONS $GW/api/matches -H 'Origin: http://localhost:3000' -H 'Access-Control-Request-Method: GET' | grep -i '^access-control-allow-origin'
curl -s -i $GW/api/users/me -H 'Origin: http://localhost:3000' | grep -iE '^HTTP|^access-control-allow-origin'

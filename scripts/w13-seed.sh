#!/usr/bin/env bash
# W-13 첫 통합용 더미 데이터 (로컬 개발용, 운영 금지). 사전 조건: compose 인프라 + 서비스 4개 + gateway 기동, facility/match 테이블 생성(서비스를 한 번 띄운 상태).
# Git Bash 의 curl 은 한글 JSON 을 UTF-8 로 보내지 않아 회원·매치 값은 영문으로 둔다 (python 으로 보내면 한글 OK).
# 시설은 서울 키 없이 SQL 로, 회원 · 매치는 게이트웨이 API 로 넣는다 (서비스 로직 검증 겸용).
set -euo pipefail
GW=${GW:-http://localhost:8000}
PW='Passw0rd!'

db() { docker compose exec -T "$1" mariadb -u"$2" -p"$2" "$3"; }

echo "[1/3] facility_db 더미 시설 2건"
db facility-db facility_svc facility_db <<'SQL'
INSERT INTO location (id, region, name, facility_name, contact, latitude, longitude, created_at, updated_at)
VALUES (1, '강남구', '더미 체육관', '다목적구장', '02-000-0000', 37.517236, 127.047325, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO service (id, location_id, service_id, name, status, start_time, end_time, is_free, url, active, created_at, updated_at)
VALUES (1, 1, 'DUMMY-001', '더미 풋살장 예약', '접수중', '09:00:00', '22:00:00', b'1', 'https://example.com/1', b'1', NOW(), NOW()),
       (2, 1, 'DUMMY-002', '더미 농구장 예약', '접수중', '09:00:00', '22:00:00', b'0', 'https://example.com/2', b'1', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
SQL

echo "[2/3] match_db 시설 스냅샷 (Kafka 동기화 W-18/19 전까지 직접 채움)"
db match-db match_svc match_db <<'SQL'
INSERT INTO facility_service_snapshot (service_id, name, location_name, region, is_free, active, source_updated_at, updated_at)
VALUES (1, '더미 풋살장 예약', '더미 체육관', '강남구', b'1', b'1', NOW(), NOW()),
       (2, '더미 농구장 예약', '더미 체육관', '강남구', b'0', b'1', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
SQL

echo "[3/3] 게이트웨이로 회원 가입 → 로그인 → 매치 개설"
signup() { # email nickname gender
  curl -s -o /dev/null -w "  signup $1 -> %{http_code}\n" -X POST "$GW/api/users" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PW\",\"gender\":\"$3\",\"nickname\":\"$2\",\"district\":\"GANGNAM\",\"sportTypes\":[\"FUTSAL\",\"BASKETBALL\"]}"
}
signup w13a@example.com dummyA MALE
signup w13b@example.com dummyB FEMALE

TOKEN=$(curl -s -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"w13a@example.com\",\"password\":\"$PW\"}" | python -c 'import sys,json;d=json.load(sys.stdin);print(d.get("accessToken") or d.get("data",{}).get("accessToken",""))' 2>/dev/null || true)
[ -n "$TOKEN" ] || { echo "  로그인 응답에서 accessToken 을 못 찾음 (응답 형식 확인 필요)"; exit 1; }

START=$(date -d '+2 days' '+%Y-%m-%dT19:00:00' 2>/dev/null || date -v+2d '+%Y-%m-%dT19:00:00')
END=$(date -d '+2 days' '+%Y-%m-%dT21:00:00' 2>/dev/null || date -v+2d '+%Y-%m-%dT21:00:00')
for spec in "1|FUTSAL|Dummy futsal match|MIXED" "2|BASKETBALL|Dummy basketball 3on3|MALE"; do
  IFS='|' read -r sid sport title gg <<<"$spec"
  curl -s -o /dev/null -w "  match $title -> %{http_code}\n" -X POST "$GW/api/matches" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"serviceId\":$sid,\"title\":\"$title\",\"description\":\"W-13 integration check\",\"startAt\":\"$START\",\"endAt\":\"$END\",\"maxParticipant\":6,\"skillLevel\":\"BEGINNER\",\"sportType\":\"$sport\",\"genderGroup\":\"$gg\"}"
done
echo "완료. 로그인: w13a@example.com / $PW"

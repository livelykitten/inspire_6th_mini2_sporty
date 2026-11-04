#!/usr/bin/env bash
# 테스트 시드 데이터: 시설 2건 + match 의 시설 스냅샷. 여러 번 실행해도 안전하다 (ON DUPLICATE KEY).
# 시설 스냅샷은 Kafka 동기화(W-18 · W-19)가 생기기 전까지 SQL 로 채운다 (W-13 시드 스크립트와 같은 방식).
# 값은 ASCII 로 둔다 (Git Bash 의 curl 이 한글 JSON 을 UTF-8 로 보내지 못하는 문제와 별개로, 터미널 인코딩 차이를 피한다).
# 담당: 반정욱 (@salieri009) · 작업 위치: tests/pipeline/fixtures.sh
set -euo pipefail
COMPOSE_CMD="${COMPOSE_CMD:-docker compose -f compose.yaml -f compose.app.yaml}"
dbx() { $COMPOSE_CMD exec -T "$1" mariadb -u"$2" -p"$2" "$3"; }

echo "[fixtures] facility_db 시설 2건"
dbx facility-db facility_svc facility_db <<'SQL'
INSERT INTO location (id, region, name, facility_name, contact, latitude, longitude, created_at, updated_at)
VALUES (1, 'Gangnam', 'Dummy Gym', 'Multi Court', '02-000-0000', 37.517236, 127.047325, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO service (id, location_id, service_id, name, status, start_time, end_time, is_free, url, active, created_at, updated_at)
VALUES (1, 1, 'DUMMY-001', 'Dummy Futsal Court', 'OPEN', '09:00:00', '22:00:00', b'1', 'https://example.com/1', b'1', NOW(), NOW()),
       (2, 1, 'DUMMY-002', 'Dummy Basketball Court', 'OPEN', '09:00:00', '22:00:00', b'0', 'https://example.com/2', b'1', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
SQL

echo "[fixtures] match_db 시설 스냅샷"
dbx match-db match_svc match_db <<'SQL'
INSERT INTO facility_service_snapshot (service_id, name, location_name, region, is_free, active, source_updated_at, updated_at)
VALUES (1, 'Dummy Futsal Court', 'Dummy Gym', 'Gangnam', b'1', b'1', NOW(), NOW()),
       (2, 'Dummy Basketball Court', 'Dummy Gym', 'Gangnam', b'0', b'1', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
SQL
echo "[fixtures] 완료"

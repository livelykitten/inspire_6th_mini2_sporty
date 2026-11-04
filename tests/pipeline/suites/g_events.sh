# 스위트 G. 이벤트 / Kafka (facility → match, 담당 이서현 · 김예림 / 검증 반정욱 · 김우석) - 10점
# 시트 TC-24 (MSA-06 · MSA-10): 시설 변경 이벤트의 발행(Outbox) · 전달 · 스냅샷 반영.
# 외부 공공데이터 API 키 없이 돌도록, 이벤트는 outbox 행 삽입 또는 토픽 직접 발행으로 만든다.
suite G "이벤트 (Kafka)" "facility-service · match-service"

KAFKA_BIN=/opt/kafka/bin
EVENT_TOPIC=facility.service.changed

event_json() { # 이름 [active true]
  printf '{"eventId":"%s","occurredAt":"%s","serviceId":1,"name":"%s","locationName":"Dummy Gym","region":"Gangnam","isFree":true,"reservationDeadlineAt":null,"active":%s}' \
    "$(new_uuid)" "$(tomorrow_ts)" "$1" "${2:-true}"
}

g1_topics_exist() {
  need_docker || return
  local list
  list="$(compose exec -T kafka sh -c "$KAFKA_BIN/kafka-topics.sh --bootstrap-server kafka:29092 --list" 2>/dev/null | tr -d '\r')"
  local t
  for t in facility.service.changed user.withdrawal.requested match.withdrawal.completed match.withdrawal.failed; do
    case "$list" in *"$t"*) ;; *) fail "토픽 없음: $t" ;; esac
    case "$list" in *"$t.DLT"*) ;; *) fail "DLT 토픽 없음: $t.DLT" ;; esac
  done
}

g2_outbox_survives_kafka_outage() {
  need_docker || return
  local has
  has="$(db_query facility-db facility_svc facility_db "SHOW TABLES LIKE 'facility_outbox'")"
  [ -n "$has" ] || { fail "대기: W-18 (facility_outbox 테이블 없음, Outbox 발행 미구현)"; return; }
  new_user g2 || return
  create_match_ready "g2-${RANDOM}" || { fail "사전 매치 생성 실패"; return; }
  local mid="$MATCH_ID" name="g2-renamed-${RANDOM}" eid payload
  eid="$(new_uuid)"
  payload="$(event_json "$name" | sed "s/\"eventId\":\"[^\"]*\"/\"eventId\":\"$eid\"/")"
  compose stop kafka >/dev/null 2>&1
  sleep 3
  # Kafka 가 멈춘 동안 쌓인 미발행 이벤트 (시설 동기화가 남긴 outbox 행과 같은 모양)
  printf "INSERT INTO facility_outbox (event_id, topic, message_key, payload, status, attempt_count, created_at) VALUES ('%s','%s','1','%s','PENDING',0,NOW(6));" \
    "$eid" "$EVENT_TOPIC" "$payload" | compose exec -T facility-db mariadb -ufacility_svc -pfacility_svc facility_db 2>/dev/null
  sleep 15
  expect_eq PENDING "$(db_query facility-db facility_svc facility_db "SELECT status FROM facility_outbox WHERE event_id='$eid'")" "Kafka 중지 중 outbox 상태"
  compose start kafka >/dev/null 2>&1
  wait_service_healthy kafka 120 || fail "kafka 복구 실패"
  local i st=""
  for i in $(seq 1 40); do
    st="$(db_query facility-db facility_svc facility_db "SELECT status FROM facility_outbox WHERE event_id='$eid'")"
    [ "$st" = "SENT" ] && break
    sleep 3
  done
  expect_eq SENT "$st" "Kafka 재기동 뒤 outbox 상태 (재시도 발행)"
  for i in $(seq 1 20); do
    req GET "/api/matches/$mid"
    [ "$(jget .serviceName)" = "$name" ] && break
    sleep 3
  done
  expect_eq "$name" "$(jget .serviceName)" "매치 쪽 시설 스냅샷 반영"
}

g3_event_updates_match_snapshot() {
  need_docker || return
  new_user g3 || return
  create_match_ready "g3-${RANDOM}" || { fail "사전 매치 생성 실패"; return; }
  local mid="$MATCH_ID" name="g3-renamed-${RANDOM}"
  # 키 "1" (serviceId) 로 이벤트를 직접 발행한다. 구독 쪽 (match-service) 만 검증한다.
  printf '1:%s\n' "$(event_json "$name")" | compose exec -T kafka sh -c \
    "$KAFKA_BIN/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic $EVENT_TOPIC --property parse.key=true --property key.separator=:" >/dev/null 2>&1
  local i
  for i in $(seq 1 20); do
    req GET "/api/matches/$mid"
    [ "$(jget .serviceName)" = "$name" ] && break
    sleep 2
  done
  expect_eq "$name" "$(jget .serviceName)" "이벤트가 매치 상세의 시설 이름에 반영"
}

g4_duplicate_event_is_ignored() {
  need_docker || return
  local eid sid name occurred payload before after logs i
  eid="$(new_uuid)"
  sid=$((9900000 + RANDOM))
  name="g4-duplicate-${RANDOM}"
  occurred="$(tomorrow_ts)"
  payload="$(printf '{\"eventId\":\"%s\",\"occurredAt\":\"%s\",\"serviceId\":%s,\"name\":\"%s\",\"locationName\":\"Duplicate Gym\",\"region\":\"Gangnam\",\"isFree\":true,\"reservationDeadlineAt\":null,\"active\":true}' "$eid" "$occurred" "$sid" "$name")"

  printf '%s:%s\n' "$sid" "$payload" | compose exec -T kafka sh -c \
    "$KAFKA_BIN/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic $EVENT_TOPIC --property parse.key=true --property key.separator=:" >/dev/null 2>&1

  before=""
  for i in $(seq 1 20); do
    before="$(db_query match-db match_svc match_db "SELECT CONCAT_WS('|', name, location_name, region, is_free, active, source_updated_at) FROM facility_service_snapshot WHERE service_id=$sid")"
    [ -n "$before" ] && break
    sleep 2
  done
  [ -n "$before" ] || { fail "첫 이벤트의 시설 스냅샷 반영 실패 (eventId=$eid)"; return; }

  printf '%s:%s\n' "$sid" "$payload" | compose exec -T kafka sh -c \
    "$KAFKA_BIN/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic $EVENT_TOPIC --property parse.key=true --property key.separator=:" >/dev/null 2>&1

  logs=""
  for i in $(seq 1 20); do
    logs="$(compose logs --since 2m match-service 2>&1)"
    printf '%s' "$logs" | grep -F "[KAFKA] duplicate ignored" | grep -F "$eid" >/dev/null && break
    sleep 2
  done
  printf '%s' "$logs" | grep -F "[KAFKA] duplicate ignored" | grep -F "$eid" >/dev/null \
    || fail "중복 무시 로그 없음 (eventId=$eid)"

  after="$(db_query match-db match_svc match_db "SELECT CONCAT_WS('|', name, location_name, region, is_free, active, source_updated_at) FROM facility_service_snapshot WHERE service_id=$sid")"
  expect_eq "$before" "$after" "중복 이벤트 뒤 시설 스냅샷 값 불변"
  expect_eq 1 "$(db_query match-db match_svc match_db "SELECT COUNT(*) FROM processed_event WHERE event_id='$eid'")" "중복 eventId processed_event 1행"
}

tc 2 "MSA-06"        "토픽 4개 + DLT 토픽 존재"                              g1_topics_exist
tc 4 "TC-24 MSA-10"  "Kafka 중지 중 outbox PENDING → 재기동 뒤 SENT · 스냅샷 반영" g2_outbox_survives_kafka_outage  "W-18"
tc 4 "TC-24 EV-01"   "시설 변경 이벤트 수신 → 매치 시설 스냅샷 반영"          g3_event_updates_match_snapshot "W-19"
tc 0 "W-38 EV-01 보완" "동일 eventId 재전달 → 중복 로그 + 스냅샷 값 불변"       g4_duplicate_event_is_ignored "W-31"

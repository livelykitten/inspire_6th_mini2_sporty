# 스위트 H. 회원 탈퇴 Saga (user-service · match-service, 담당 송석주 · 민경완 / 검증 김예림) - 5점
# 시트 TC-04 (USR-04) · TC-25 (OPT-10): 탈퇴 → 매치 정리 → WITHDRAWN, 중복 요청 응답, 실패 보상.
# 응답 코드 규약은 설계 문서(PR #23): 처리 중 재요청 409 WITHDRAWAL_ALREADY_PENDING, 탈퇴 완료 뒤 401 USER_ALREADY_WITHDRAWN.
suite H "탈퇴 Saga" "user-service · match-service"

h1_withdraw_success() {
  new_user h1 || return
  local tok="$U_TOKEN"
  req DELETE /api/users/me -t "$tok" -d "{\"password\":\"$U_PW\"}"
  expect_code "202|204" "탈퇴 요청"
  # Saga 에서는 PENDING → WITHDRAWN 으로 잠시 걸릴 수 있다. 로그인이 막힐 때까지 기다린다.
  local i code=""
  for i in $(seq 1 20); do
    req POST /api/auth/login -d "{\"email\":\"$U_EMAIL\",\"password\":\"$U_PW\"}"
    [ "$HTTP_CODE" != "200" ] && break
    sleep 2
  done
  case "$HTTP_CODE" in 400|401|403|404|409) ;; *) fail "탈퇴 뒤 로그인이 막히지 않음 (HTTP $HTTP_CODE)" ;; esac
  req GET /api/users/me -t "$tok"
  case "$HTTP_CODE" in 401|403|404) ;; *) fail "탈퇴 뒤 기존 토큰으로 내 정보 조회가 막히지 않음 (HTTP $HTTP_CODE)" ;; esac
}

h2_duplicate_withdraw_code() {
  new_user h2 || return
  local tok="$U_TOKEN"
  req DELETE /api/users/me -t "$tok" -d "{\"password\":\"$U_PW\"}"
  expect_code "202|204" "첫 탈퇴 요청"
  req DELETE /api/users/me -t "$tok" -d "{\"password\":\"$U_PW\"}"
  case "$HTTP_BODY" in
    *WITHDRAWAL_ALREADY_PENDING*|*USER_ALREADY_WITHDRAWN*) ;;
    *) fail "대기: W-22 (중복 탈퇴 오류 코드 WITHDRAWAL_ALREADY_PENDING / USER_ALREADY_WITHDRAWN 없음, HTTP $HTTP_CODE)" ;;
  esac
}

h3_compensation_on_failure() {
  # 보상 시나리오는 Config 의 saga.fail-cleanup=true 로 match 쪽 정리를 일부러 실패시킨다 (W-32).
  local cfg
  cfg="$(curl -s -m 5 http://127.0.0.1:8888/match-service/default 2>/dev/null || true)"
  case "$cfg" in
    *saga.fail-cleanup*) fail "구현 필요: 보상 시나리오 자동화 (saga.fail-cleanup 이 생기면 설정 변경 · 재기동 · 탈퇴 · ACTIVE 복원 확인)" ;;
    *) fail "대기: W-32 (saga.fail-cleanup 설정과 보상 이벤트 EV-04 미구현)" ;;
  esac
}

tc 2 "TC-04 USR-04"  "탈퇴 요청 → 로그인 · 토큰 차단"                         h1_withdraw_success
tc 2 "USR-04 · PR #23" "중복 탈퇴 요청은 규약된 오류 코드"                     h2_duplicate_withdraw_code  "W-22"
tc 1 "TC-25 OPT-10"  "실패 보상: 회원 상태가 ACTIVE 로 복원"                   h3_compensation_on_failure  "W-32"

# W-38 서비스 중지 시나리오는 반드시 이 파일의 마지막에 등록한다. 기존 테스트 불변, 0점.
h4_pending_member_create_exact() {
  need_docker || return
  # 기존 시설 스냅샷을 조회만 한다. 시연 시설을 fixtures.sh로 덮어쓰지 않는다.
  local sid uid tok title before after pending state stopped old_exit i
  sid="$(db_query match-db match_svc match_db 'SELECT service_id FROM facility_service_snapshot WHERE active=1 ORDER BY service_id LIMIT 1')"
  [[ "$sid" =~ ^[0-9]+$ ]] || { fail "사전 조건: 활성 시설 스냅샷 없음 또는 DB 조회 실패"; return; }
  new_user h4 || return
  uid="$(jget .userId)"; tok="$U_TOKEN"
  [[ "$uid" =~ ^[0-9]+$ ]] || { fail "사전 로그인 응답에 userId 없음"; return; }
  # ACTIVE 상태에서는 같은 시설/본문으로 생성 가능함을 먼저 확인한다.
  create_match_ready "h4-active-${uid}" FUTSAL "$sid" || { fail "사전 ACTIVE 매치 생성 실패 (HTTP $HTTP_CODE)"; return; }
  expect_code 201 "ACTIVE 사전 매치 생성" || return
  title="h4-pending-${uid}-${RANDOM}"
  before="$(db_query match-db match_svc match_db "SELECT COUNT(*) FROM \`match\` WHERE title='$title'")"
  expect_eq 0 "$before" "거부 검증용 제목의 사전 매치 수" || return
  old_exit="$(trap -p EXIT)"
  # 조기 종료 시에도 복구를 시도한다. 강제 SIGKILL/시간 초과는 기존 runner 복구가 담당한다.
  trap 'compose start kafka >/dev/null 2>&1; rm -rf "$TMP_DIR"' EXIT
  if ! compose stop kafka >/dev/null 2>&1; then
    fail "Kafka 중지 명령 실패"
    compose start kafka >/dev/null 2>&1 || fail "Kafka 재기동 명령 실패"
    wait_service_healthy kafka 120 || fail "Kafka 복구 실패"
    trap - EXIT; [ -z "$old_exit" ] || eval "$old_exit"
    return
  fi
  stopped="$(compose ps --all --format '{{.Service}} {{.State}}' 2>/dev/null | awk '$1=="kafka" {print $2}' | tr -d '\r')"
  if [ "$stopped" != "exited" ]; then
    fail "Kafka 중지 확인 실패 (state=$stopped)"
  else
    req DELETE /api/users/me -t "$tok" -d "{\"password\":\"$U_PW\"}"
    expect_code 202 "탈퇴 접수" || true
    pending="$(db_query user-db user_svc user_db "SELECT status FROM user WHERE id=$uid")"
    expect_eq WITHDRAWAL_PENDING "$pending" "매치 생성 직전 회원 상태" || true
    if [ "$HTTP_CODE" = 202 ] && [ "$pending" = WITHDRAWAL_PENDING ]; then
      create_match "$title" FUTSAL "$sid" || true
      expect_code 401 "처리 중 회원의 기존 토큰으로 매치 생성"
      expect_eq USER_WITHDRAWN "$(jget .code)" "매치 생성 거부 오류 code"
      after="$(db_query match-db match_svc match_db "SELECT COUNT(*) FROM \`match\` WHERE title='$title'")"
      expect_eq 0 "$after" "Kafka 중지 중 거부된 매치 미생성"
      expect_eq WITHDRAWAL_PENDING "$(db_query user-db user_svc user_db "SELECT status FROM user WHERE id=$uid")" "거부 요청 후에도 처리 중"
    fi
  fi
  # 실패 여부와 무관하게 Kafka를 복구한 뒤에만 이 테스트를 끝낸다.
  if compose start kafka >/dev/null 2>&1 && wait_service_healthy kafka 120; then
    trap - EXIT; [ -z "$old_exit" ] || eval "$old_exit"
    state=""
    for i in $(seq 1 30); do
      state="$(db_query user-db user_svc user_db "SELECT status FROM user WHERE id=$uid")"
      [ "$state" = WITHDRAWN ] && break
      sleep 2
    done
    expect_eq WITHDRAWN "$state" "Kafka 복구 후 정상 Saga 완료"
    expect_eq 0 "$(db_query match-db match_svc match_db "SELECT COUNT(*) FROM \`match\` WHERE title='$title'")" "Kafka 복구 후에도 거부된 매치 미생성"
    note "Kafka 재기동/healthy 확인; 회원 $uid, 기존 토큰 사용, 거부 매치 0행"
  else
    fail "Kafka 재기동/healthy 복구 실패 (EXIT에서 재기동 재시도)"
  fi
}

tc 0 "W-38 USR-04 EM-01" "처리 중 회원 생성 거부: 401 + USER_WITHDRAWN · 매치 0행" h4_pending_member_create_exact

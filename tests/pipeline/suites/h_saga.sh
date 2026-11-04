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
  # 로그인은 WITHDRAWAL_PENDING 에서도 막히지만, 내 정보 조회(USR-06)는 PENDING 동안 허용이 확정된 규약이다 (설계 문서 PR #23).
  # 그래서 토큰 차단은 Saga 가 WITHDRAWN 으로 확정한 뒤부터이므로, 막힐 때까지 기다린 다음 확인한다.
  for i in $(seq 1 20); do
    req GET /api/users/me -t "$tok"
    [ "$HTTP_CODE" != "200" ] && break
    sleep 1
  done
  case "$HTTP_CODE" in 401|403|404) ;; *) fail "탈퇴 확정 뒤에도 기존 토큰으로 내 정보 조회가 막히지 않음 (HTTP $HTTP_CODE)" ;; esac
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

# match 를 실패 주입 설정(SAGA_FAIL_CLEANUP)으로 다시 띄운다. compose.app.yaml 이 이 값을 match 의 saga.fail-cleanup 으로 넘긴다.
h_restart_match() { # true|false
  SAGA_FAIL_CLEANUP="$1" $COMPOSE_CMD up -d match-service >/dev/null 2>&1
  wait_service_healthy match-service 150 && wait_gateway_stable 40 150
}

h3_compensation_on_failure() {
  # 보상 시나리오 (W-32): match 쪽 정리를 일부러 실패시키면 재시도 소진 → EV-04 → user 가 PENDING 에서 ACTIVE 로 복원한다.
  need_docker || return
  h_restart_match true || { fail "실패 주입 설정으로 match-service 재기동 실패"; h_restart_match false; return; }
  if new_user h3; then
    req DELETE /api/users/me -t "$U_TOKEN" -d "{\"password\":\"$U_PW\"}"
    expect_code "202|204" "탈퇴 요청"
    # 로그인은 PENDING · WITHDRAWN 에서 막히고 ACTIVE 에서만 된다. 다시 200 이 되면 보상으로 복원된 것이다.
    local i
    for i in $(seq 1 30); do
      sleep 2
      req POST /api/auth/login -d "{\"email\":\"$U_EMAIL\",\"password\":\"$U_PW\"}"
      [ "$HTTP_CODE" = "200" ] && break
    done
    expect_code 200 "보상 뒤 로그인 (ACTIVE 복원)"
  fi
  h_restart_match false || fail "실패 주입 해제 뒤 match-service 복구 실패"
}

tc 2 "TC-04 USR-04"  "탈퇴 요청 → 로그인 · 토큰 차단"                         h1_withdraw_success
tc 2 "USR-04 · PR #23" "중복 탈퇴 요청은 규약된 오류 코드"                     h2_duplicate_withdraw_code  "W-22"
tc 1 "TC-25 OPT-10"  "실패 보상: 회원 상태가 ACTIVE 로 복원"                   h3_compensation_on_failure  "W-32"

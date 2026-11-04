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

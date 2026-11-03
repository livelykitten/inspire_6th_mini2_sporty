# 스위트 F. 장애 격리 (gateway · match · facility · user, 담당 이서현 · 민경완 / 검증 김우석 · 이서현) - 10점
# 시트 TC-22 · TC-23 (MSA-10 · OPT-05): 서비스 하나가 멈춰도 다른 기능은 계속 동작해야 한다. 컨테이너를 실제로 멈춘다.
# 각 테스트는 끝에 멈춘 서비스를 다시 올리고 복구를 기다린다 (뒤 스위트에 영향이 없도록).
suite F "장애 격리" "gateway · match · facility · user"

f1_facility_down_match_search_ok() {
  need_docker || return
  new_user f1 || return
  local title="f1-${RANDOM}"
  create_match "$title" || { fail "사전 매치 생성 실패"; return; }
  local mid="$MATCH_ID"
  compose stop facility-service >/dev/null 2>&1
  sleep 2
  req GET "/api/matches?titleKeyword=$title"
  expect_code 200 "facility 중지 중 매치 검색"
  expect_contains "$title" "facility 중지 중 검색 결과 (match 의 시설 스냅샷으로 응답)"
  req GET "/api/matches/$mid"
  expect_code 200 "facility 중지 중 매치 상세"
  compose start facility-service >/dev/null 2>&1
  wait_service_healthy facility-service 150 || fail "facility-service 복구 실패"
  # facility-service 는 ddl-auto: create-drop 이라 재기동하면 시설 데이터가 사라진다 (이슈 #20). 뒤 테스트를 위해 시드를 복구한다.
  bash tests/pipeline/fixtures.sh >/dev/null 2>&1 || note "시설 시드 복구 실패"
  note "재기동으로 시설 데이터가 초기화됨 (#20)"
}

f2_user_down_match_detail_ok() {
  need_docker || return
  new_user f2 || return
  create_match "f2-${RANDOM}" || { fail "사전 매치 생성 실패"; return; }
  local mid="$MATCH_ID"
  compose stop user-service >/dev/null 2>&1
  sleep 2
  req GET "/api/matches/$mid"
  expect_code 200 "user 중지 중 매치 상세 (참가자 닉네임은 비어도 됨)"
  req GET /api/matches
  expect_code 200 "user 중지 중 매치 목록"
  compose start user-service >/dev/null 2>&1
  wait_service_healthy user-service 150 || fail "user-service 복구 실패"
  wait_user_route 150 || fail "게이트웨이가 user-service 로 다시 라우팅하지 못함"
}

f3_match_down_fast_fail_and_recover() {
  need_docker || return
  compose stop match-service >/dev/null 2>&1
  sleep 2
  req GET /api/matches
  case "$HTTP_CODE" in
    500|502|503|504) ;;
    *) fail "match 중지 중 게이트웨이 응답 HTTP $HTTP_CODE (5xx 기대)" ;;
  esac
  [ "$HTTP_MS" -lt 5000 ] || fail "match 중지 중 응답이 ${HTTP_MS}ms (5초 안에 실패해야 함, 매달리면 안 됨)"
  note "중지 중 응답 ${HTTP_MS}ms"
  compose start match-service >/dev/null 2>&1
  local secs
  secs="$(wait_gateway_ok 150)" || { fail "match 재기동 뒤 150초 안에 복구되지 않음"; return; }
  [ "$secs" -le 120 ] || fail "복구에 ${secs}초 (120초 이내 기대)"
  note "재기동 뒤 게이트웨이 복구 ${secs}초"
}

tc 4 "TC-22 MSA-10"  "facility 중지 중에도 매치 검색 200"            f1_facility_down_match_search_ok
tc 3 "TC-23 OPT-05"  "user 중지 중에도 매치 상세 · 목록 200"          f2_user_down_match_detail_ok  "W-17"
tc 3 "MSA-10"        "match 중지 시 5초 안에 5xx · 재기동 뒤 복구"     f3_match_down_fast_fail_and_recover

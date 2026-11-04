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
  # 앞 테스트(E · F)가 match 를 재기동했을 수 있다. 게이트웨이 라우트가 안정된 뒤에 user 를 멈춰야
  # user 장애가 아닌 게이트웨이 캐시 갱신 때문에 503 이 나는 것을 섞지 않는다.
  wait_gateway_stable 40 150 || { fail "사전 조건: 게이트웨이 → match 라우트가 안정되지 않음"; return; }
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

# 배점 0 (W-38 보완): 회원 서비스가 멈춘 동안의 매치 참가. 시트 오류 코드 탭 USER_SERVICE_UNAVAILABLE (503, EM-06).
# user-service 를 멈추므로 파일 맨 뒤에 두고, 끝나면 다시 올려 게이트웨이 라우팅까지 복구를 기다린다.
f4_user_down_join_503() {
  need_docker || return
  new_user f4a || return
  create_match_ready "f4-${RANDOM}" || { fail "사전 매치 생성 실패"; return; }
  local mid="$MATCH_ID"
  new_user f4b || return
  local token_b="$U_TOKEN"
  # 바로 앞 f3 이 match 를 재기동했다. 게이트웨이 → match 라우트가 연속 5번 200 일 때 user 를 멈춰야
  # 게이트웨이 캐시 갱신 때문에 생기는 503 이 user 장애의 503 과 섞이지 않는다.
  local ok=0 waited=0
  while [ "$ok" -lt 5 ]; do
    req GET "/api/matches/$mid"
    if [ "$HTTP_CODE" = "200" ]; then ok=$((ok + 1)); else ok=0; fi
    [ "$waited" -ge 150 ] && { fail "사전 조건: 게이트웨이 → match 라우트가 안정되지 않음"; return; }
    sleep 1; waited=$((waited + 1))
  done
  local before
  before="$(jget .currentParticipantCount)"
  compose stop user-service >/dev/null 2>&1
  sleep 2
  req POST "/api/matches/$mid/participants" -t "$token_b"
  expect_code 503 "user 중지 중 매치 참가"
  expect_eq "USER_SERVICE_UNAVAILABLE" "$(jget .code)" "응답 code"
  compose start user-service >/dev/null 2>&1
  wait_service_healthy user-service 150 || fail "user-service 복구 실패"
  wait_user_route 150 || fail "게이트웨이가 user-service 로 다시 라우팅하지 못함"
  req GET "/api/matches/$mid"
  expect_code 200 "user 복구 뒤 매치 상세"
  expect_eq "$before" "$(jget .currentParticipantCount)" "재조회 참가 인원 (변화 없어야 함)"
}

tc 0 "EM-06 MSA-10"  "user 중지 중 매치 참가는 503 + USER_SERVICE_UNAVAILABLE, 인원 변화 없음"  f4_user_down_join_503

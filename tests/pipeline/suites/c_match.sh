# 스위트 C. 매치 (match-service, 담당 김예림 · 민경완 / 검증 김우석) - 20점
# 시트 TC-09 ~ TC-16 (EM-01 ~ 08) / 의존: W-08. 시설 스냅샷은 fixtures.sh 가 채운다.
suite C "매치" "match-service"

c1_create_and_detail() {
  new_user c1 || return
  local title="c1-${RANDOM}"
  create_match "$title" || { fail "매치 생성 실패 (HTTP $HTTP_CODE ${HTTP_BODY:0:120})"; return; }
  expect_code 201 "매치 생성"
  req GET "/api/matches/$MATCH_ID"
  expect_code 200 "상세 조회 (비로그인)"
  expect_contains "$title" "상세에 제목"
}

c2_search_conditions() {
  new_user c2 || return
  local kw="c2key${RANDOM}"
  create_match "$kw match" FUTSAL || { fail "매치 생성 실패 (HTTP $HTTP_CODE)"; return; }
  req GET "/api/matches?titleKeyword=$kw"
  expect_code 200 "제목 검색"
  expect_contains "$kw" "검색 결과에 생성한 매치"
  req GET "/api/matches?titleKeyword=nomatch${RANDOM}${RANDOM}"
  expect_code 200 "없는 키워드 검색"
  expect_not_contains "$kw" "없는 키워드 결과"
  req GET "/api/matches?titleKeyword=$kw&sportType=BASKETBALL"
  expect_code 200 "종목 조건 검색"
  expect_not_contains "$kw" "다른 종목 조건에서 제외"
}

c3_modify_owner_only() {
  new_user c3 || return
  local owner="$U_TOKEN"
  create_match "c3-${RANDOM}" || { fail "매치 생성 실패"; return; }
  local mid="$MATCH_ID"
  local body="{\"title\":\"c3-modified\",\"description\":\"changed\",\"startAt\":\"$(future_ts start)\",\"endAt\":\"$(future_ts end)\",\"maxParticipant\":8,\"skillLevel\":\"BEGINNER\",\"genderGroup\":\"MIXED\"}"
  req PUT "/api/matches/$mid" -t "$owner" -d "$body"
  expect_code 200 "생성자 수정"
  req GET "/api/matches/$mid"
  expect_contains "c3-modified" "수정 내용 반영"
  new_user c3b || return
  req PUT "/api/matches/$mid" -t "$U_TOKEN" -d "$body"
  expect_code "403|401" "생성자가 아닌 사용자의 수정은 거부"
}

c4_join_and_leave() {
  new_user c4 || return
  create_match "c4-${RANDOM}" FUTSAL 1 6 || { fail "매치 생성 실패"; return; }
  local mid="$MATCH_ID"
  req GET "/api/matches/$mid"
  local before; before="$(jget .currentParticipantCount)"
  new_user c4b || return
  req POST "/api/matches/$mid/participants" -t "$U_TOKEN"
  expect_code 201 "참여"
  req GET "/api/matches/$mid"
  expect_eq $((before + 1)) "$(jget .currentParticipantCount)" "참여 뒤 인원"
  req DELETE "/api/matches/$mid/participants/me" -t "$U_TOKEN"
  expect_code 204 "참여 취소"
  req GET "/api/matches/$mid"
  expect_eq "$before" "$(jget .currentParticipantCount)" "취소 뒤 인원"
}

c5_my_matches_and_delete() {
  new_user c5 || return
  local owner="$U_TOKEN"
  create_match "c5-${RANDOM}" || { fail "매치 생성 실패"; return; }
  local mid="$MATCH_ID"
  req GET /api/matches/me -t "$owner"
  expect_code 200 "내 매치 목록"
  expect_contains "$mid" "내 매치에 생성한 매치"
  req DELETE "/api/matches/$mid" -t "$owner"
  expect_code 204 "생성자 삭제"
  req GET "/api/matches/$mid"
  expect_code 404 "삭제 뒤 상세"
  expect_contains "MATCH_NOT_FOUND" "삭제 뒤 오류 코드"
}

tc 4 "TC-09 EM-01 · TC-11 EM-03"  "매치 생성 + 상세 조회"                 c1_create_and_detail
tc 4 "TC-10 EM-02"                "매치 목록 · 검색 조건"                   c2_search_conditions
tc 4 "TC-12 EM-04"                "매치 수정 (생성자만)"                    c3_modify_owner_only
tc 4 "TC-14 EM-06 · TC-15 EM-07"  "매치 참여 · 참여 취소 (인원 반영)"      c4_join_and_leave
tc 4 "TC-13 EM-05 · TC-16 EM-08"  "내 매치 목록 + 삭제 뒤 404"             c5_my_matches_and_delete

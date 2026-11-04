# 스위트 D. 시설 (facility-service, 담당 이서현 / 검증 김예림) - 10점
# 시트 TC-20 · TC-21 (FC-01 · FC-02) / 의존: W-09. 시설 데이터는 fixtures.sh 가 채운다.
suite D "시설" "facility-service"

d1_list() {
  req GET /api/services
  expect_code 200 "시설 목록"
  [ -n "$(jget '[0]')" ] || fail "목록이 비어 있음 (fixtures.sh 실행 여부 확인)"
}

d2_detail() {
  req GET /api/services/1
  expect_code 200 "시설 상세"
  expect_contains "Dummy" "상세에 시설 이름"
}

d3_invalid_input() {
  req GET /api/services/not-a-number
  expect_code 400 "숫자가 아닌 ID"
  req GET /api/services/9999999
  expect_code "404|400" "없는 시설 ID"
}

tc 4 "TC-20 FC-01"  "체육시설 목록 조회"              d1_list
tc 4 "TC-21 FC-02"  "체육시설 상세 조회"              d2_detail
tc 2 "FC-02"        "잘못된 ID · 없는 시설은 4xx"      d3_invalid_input

# 배점 0 (W-38 보완): 상태 코드 하나 + 응답 code 를 시트 오류 코드 탭 기준으로 본다. 실패해도 코드는 고치지 않고 이슈로 남긴다.
d4_not_found_code() {
  req GET /api/services/9999999
  expect_code 404 "없는 시설 ID"
  expect_eq "SERVICE_NOT_FOUND" "$(jget .code)" "응답 code"
}

tc 0 "FC-02"        "없는 시설 ID 는 404 + SERVICE_NOT_FOUND"  d4_not_found_code

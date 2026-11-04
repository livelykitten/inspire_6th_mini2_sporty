# 스위트 B. 회원 (user-service, 담당 송석주 / 검증 이서현) - 20점
# 시트 TC-01 ~ TC-08 (USR-01 ~ 06, PR-01 · 02) / 의존: W-07 · W-11
suite B "회원" "user-service"

b1_signup() {
  local email="tb1$(date +%s)${RANDOM}@example.com"
  local body="{\"email\":\"$email\",\"password\":\"$PW\",\"gender\":\"MALE\",\"nickname\":\"tb1${RANDOM}\",\"district\":\"GANGNAM\",\"sportTypes\":[\"FUTSAL\"]}"
  req POST /api/users -d "$body"
  expect_code 201 "가입"
  req POST /api/users -d "$body"
  expect_code "409|400" "같은 이메일 재가입은 거부"
}

b2_login_and_me() {
  new_user b2 || return
  req GET /api/users/me -t "$U_TOKEN"
  expect_code 200 "내 정보 조회"
  expect_contains "$U_EMAIL" "내 정보에 가입 이메일"
  req POST /api/auth/login -d "{\"email\":\"$U_EMAIL\",\"password\":\"wrong-password-1\"}"
  expect_code "401|400" "틀린 비밀번호 로그인은 거부"
}

b3_refresh() {
  new_user b3 || return
  req POST /api/auth/refresh -d "{\"refreshToken\":\"$U_REFRESH\"}"
  expect_code 200 "토큰 재발급"
  local at2; at2="$(jget .accessToken)"
  [ -n "$at2" ] || { fail "재발급 응답에 accessToken 없음"; return; }
  req GET /api/users/me -t "$at2"
  expect_code 200 "재발급한 토큰으로 내 정보"
  req POST /api/auth/refresh
  expect_code "401|400" "리프레시 토큰 없이 재발급은 거부"
}

b4_logout_revokes_refresh() {
  new_user b4 || return
  req POST /api/auth/logout -t "$U_TOKEN"
  expect_code "204|200" "로그아웃"
  req POST /api/auth/refresh -d "{\"refreshToken\":\"$U_REFRESH\"}"
  expect_code "401|400" "로그아웃한 리프레시 토큰은 사용 불가"
}

b5_profile_update_get() {
  new_user b5 || return
  local nick="upd${RANDOM}${RANDOM}"   # 닉네임은 중복 불가라 실행마다 다르게 한다
  req PUT /api/profiles/me -t "$U_TOKEN" -d "{\"nickname\":\"$nick\",\"district\":\"SONGPA\",\"sportTypes\":[\"BASKETBALL\"]}"
  expect_code 200 "프로필 수정"
  expect_contains "$nick" "수정한 닉네임"
  req GET /api/users/me -t "$U_TOKEN"
  expect_contains "$nick" "내 정보에 수정 반영"
  req PUT /api/profiles/me -t "$U_TOKEN" -d '{"nickname":"","district":"SONGPA","sportTypes":[]}'
  expect_code 400 "빈 닉네임은 거부"
}

tc 4 "TC-01 USR-01"          "회원 가입 + 중복 이메일 거부"            b1_signup
tc 4 "TC-02 USR-02 · TC-06 USR-06" "로그인 + 내 정보 조회"             b2_login_and_me
tc 4 "TC-05 USR-05"          "토큰 재발급"                             b3_refresh
tc 4 "TC-03 USR-03"          "로그아웃 후 리프레시 토큰 거부"          b4_logout_revokes_refresh
tc 4 "TC-07 PR-01 · TC-08 PR-02" "프로필 수정 + 반영 확인"             b5_profile_update_get

# W-38 추가 계약 검증: 기존 배점/테스트는 유지하고 아래 항목은 모두 0점이다.
b6_duplicate_email_exact() {
  new_user b6 || return
  # 닉네임 충돌과 구분하기 위해 이메일만 재사용하고 닉네임은 새로 만든다.
  local nick="b6dup${RANDOM}${RANDOM}"
  req POST /api/users -d "{\"email\":\"$U_EMAIL\",\"password\":\"$U_PW\",\"gender\":\"MALE\",\"nickname\":\"$nick\",\"district\":\"GANGNAM\",\"sportTypes\":[\"FUTSAL\"]}"
  expect_code 409 "동일 이메일 재가입"
  expect_eq DUPLICATE_EMAIL "$(jget .code)" "동일 이메일 오류 code"
}

b7_wrong_password_exact() {
  new_user b7 || return
  req POST /api/auth/login -d "{\"email\":\"$U_EMAIL\",\"password\":\"wrong-password-1\"}"
  expect_code 401 "틀린 비밀번호 로그인"
  expect_eq LOGIN_FAILED "$(jget .code)" "로그인 오류 code"
}

b8_logged_out_refresh_exact() {
  new_user b8 || return
  local old_rt="$U_REFRESH"
  [ -n "$old_rt" ] || { fail "사전 로그인 응답에 refreshToken 없음"; return; }
  req POST /api/auth/logout -t "$U_TOKEN"
  expect_code 204 "사전 로그아웃" || return
  req POST /api/auth/refresh -d "{\"refreshToken\":\"$old_rt\"}"
  expect_code 401 "로그아웃한 RT 재발급"
  expect_eq INVALID_REFRESH_TOKEN "$(jget .code)" "재발급 오류 code"
}

tc 0 "W-38 TC-01 USR-01" "동일 이메일: 409 + DUPLICATE_EMAIL" b6_duplicate_email_exact
tc 0 "W-38 TC-02 USR-02" "틀린 비밀번호: 401 + LOGIN_FAILED" b7_wrong_password_exact
tc 0 "W-38 TC-05 USR-05" "로그아웃 RT: 401 + INVALID_REFRESH_TOKEN" b8_logged_out_refresh_exact

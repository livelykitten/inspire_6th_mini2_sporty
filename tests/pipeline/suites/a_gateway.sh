# 스위트 A. 게이트웨이 · 보안 (gateway-service, 담당 김우석) - 15점
# 요구사항 MSA-03: 라우팅 · JWT · X-User-Id · CORS · 요청 로그 / 의존: W-12 · W-26
suite A "게이트웨이 · 보안" "gateway-service"

a1_no_token() {
  req GET /api/users/me
  expect_code 401 "토큰 없이 /api/users/me"
}

a2_spoofed_user_header() {
  # 클라이언트가 보낸 X-User-Id 는 믿지 않는다. 헤더만으로는 인증되면 안 된다.
  req GET /api/users/me -H 'X-User-Id: 999'
  expect_code 401 "X-User-Id 위조"
  req GET /api/matches/me -H 'X-User-Id: 999'
  expect_code 401 "X-User-Id 위조 (/api/matches/me)"
}

a3_internal_blocked() {
  # /internal/** 은 서비스끼리만 쓴다. 게이트웨이에 라우트가 없어 밖에서는 404.
  req GET /internal/users/1/status
  expect_code 404 "/internal/users/1/status"
  req GET /internal/facilities/sync
  expect_code 404 "/internal/facilities/sync"
}

a4_cors_single_header() {
  if [ -z "$CORS_ORIGIN" ]; then
    skip "CORS_ORIGIN 이 비어 있음 (같은 오리진 배포)"
    return
  fi
  req OPTIONS /api/matches -H "Origin: $CORS_ORIGIN" -H 'Access-Control-Request-Method: GET'
  expect_code 200 "preflight"
  expect_eq 1 "$(header_count access-control-allow-origin)" "Access-Control-Allow-Origin 헤더 개수 (중복되면 브라우저가 막는다)"
}

tc 4 "MSA-03"  "토큰 없는 보호 API 는 401"                 a1_no_token
tc 4 "MSA-03"  "X-User-Id 위조 헤더만으로는 401"          a2_spoofed_user_header
tc 4 "W-26"    "/internal 경로는 외부에서 404"             a3_internal_blocked
tc 3 "MSA-03"  "CORS preflight 허용 헤더가 정확히 1회"     a4_cors_single_header

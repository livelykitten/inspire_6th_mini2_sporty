# 스위트 E. AI (ai-service, 담당 서정운 / 검증 민경완) - 10점
# 시트 TC-17 ~ TC-19 (AI-01 ~ 03) / 의존: W-10 · W-20.
# OpenAI 키 없이 도는 계약 테스트다: 인증 · 라우팅 · 입력 검증 · 의존 서비스 장애 매핑. 생성 품질은 범위 밖.
suite E "AI" "ai-service"

e1_auth_required() {
  req GET /api/ai/matches/recommendations
  expect_code 401 "추천은 로그인 필요"
  req POST /api/ai/matches -d '{"prompt":"futsal tomorrow"}'
  expect_code 401 "AI 매치 생성은 로그인 필요"
}

e2_route_and_validation() {
  new_user e2 || return
  # 키가 없으면 OpenAI 호출만 실패(500)한다. 404(라우트 없음) · 503(의존 장애) 이 아니면 라우트는 살아 있다.
  req GET /api/ai/matches/recommendations -t "$U_TOKEN"
  expect_code "200|500" "추천 라우트 도달"
  req POST /api/ai/matches/search -t "$U_TOKEN" -d '{}'
  expect_code 400 "검색: 빈 요청은 입력 검증 실패"
  req POST /api/ai/matches -t "$U_TOKEN" -d '{}'
  expect_code 400 "생성: 빈 요청은 입력 검증 실패"
}

e3_dependency_failure_503() {
  need_docker || return
  new_user e3 || return
  local tok="$U_TOKEN"
  compose stop match-service >/dev/null 2>&1
  sleep 3
  req GET /api/ai/matches/recommendations -t "$tok"
  expect_code 503 "match-service 중지 중 추천"
  expect_contains "AI_DEPENDENCY_UNAVAILABLE" "의존 서비스 장애 오류 코드"
  compose start match-service >/dev/null 2>&1
  wait_service_healthy match-service 150 || fail "match-service 가 다시 healthy 가 되지 않음"
  wait_gateway_ok 150 >/dev/null || fail "게이트웨이가 match 로 다시 라우팅하지 못함"
}

tc 3 "TC-18 AI-02 · TC-19 AI-03"  "AI 엔드포인트는 인증 필요"                       e1_auth_required
tc 3 "TC-17 AI-01 · TC-18"        "라우트 도달 + 입력 검증 (OpenAI 키 없이)"          e2_route_and_validation
tc 4 "IN-03 · IN-04"              "match 중지 시 503 AI_DEPENDENCY_UNAVAILABLE + 복구" e3_dependency_failure_503

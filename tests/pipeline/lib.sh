#!/usr/bin/env bash
# MSA 테스트 파이프라인 공통 라이브러리 (이슈 #43).
# 담당: 반정욱 (@salieri009) · 작업 위치: tests/pipeline/lib.sh
#
# 테스트 정의:   tc <점수> <요구사항/TC> <이름> <함수> [대기 WBS]
# 단언:          expect_code <기대코드>   expect_eq <기대> <실제> <설명>   expect_contains <문자열> <설명>   fail <메시지>
# 요청:          req METHOD /경로 [-d JSON] [-t 토큰] [-H '헤더: 값']   → HTTP_CODE · HTTP_BODY · HTTP_MS
# 사용자 만들기:  new_user <라벨>  → U_EMAIL · U_PW · U_TOKEN · U_REFRESH
# 건너뛰기:      skip <이유>  (점수 대상에서 제외)
#
# 환경변수: BASE(기본 http://localhost:8000)  COMPOSE_CMD  NO_DOCKER=1(컨테이너 제어 테스트 건너뜀)  CORS_ORIGIN
set -u

BASE="${BASE:-http://localhost:8000}"
BASE="${BASE%/}"
COMPOSE_CMD="${COMPOSE_CMD:-docker compose -f compose.yaml -f compose.app.yaml}"
CORS_ORIGIN="${CORS_ORIGIN-http://localhost}"
NO_DOCKER="${NO_DOCKER:-0}"
RESULTS_DIR="${RESULTS_DIR:-tests/pipeline/results}"
PW='Passw0rd!'

# 실제로 실행되는 파이썬을 고른다 (Windows 에는 python3 가 스토어 안내용 가짜 실행 파일인 경우가 있다)
PY=""
for _c in python3 python py; do
  if command -v "$_c" >/dev/null 2>&1 && "$_c" -c 'import sys' >/dev/null 2>&1; then PY="$_c"; break; fi
done
[ -n "$PY" ] || { echo "실행 가능한 python3 (또는 python) 이 필요합니다" >&2; exit 2; }
export PY

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
TMP_BODY="$TMP_DIR/body"
TMP_HDR="$TMP_DIR/hdr"

TC_LIST=()          # "점수|참조|이름|함수|대기"
CURRENT_SUITE=""
SUITE_LIST=()       # "ID|이름|서비스|만점"

suite() { # ID 이름 서비스
  CURRENT_SUITE="$1"
  SUITE_LIST+=("$1|$2|$3")
}

tc() { # 점수 참조 이름 함수 [대기]
  TC_LIST+=("$CURRENT_SUITE|$1|$2|$3|$4|${5:-}")
}

# ------------------------------------------------------------------ HTTP
req() {
  local method="$1" path="$2"; shift 2
  local args=(-s -m 20 -o "$TMP_BODY" -D "$TMP_HDR" -w '%{http_code} %{time_total}' -X "$method" "$BASE$path")
  while [ $# -gt 0 ]; do
    case "$1" in
      -d) args+=(-H 'Content-Type: application/json' --data-binary "$2"); shift 2 ;;
      -t) args+=(-H "Authorization: Bearer $2"); shift 2 ;;
      -H) args+=(-H "$2"); shift 2 ;;
      *) shift ;;
    esac
  done
  local out
  out="$(curl "${args[@]}" 2>/dev/null || echo '000 0')"
  HTTP_CODE="${out%% *}"
  HTTP_MS="$("$PY" -c "import sys; print(int(float(sys.argv[1])*1000))" "${out##* }" 2>/dev/null || echo 0)"
  HTTP_BODY="$(cat "$TMP_BODY" 2>/dev/null || true)"
}

# JSON 경로 읽기: jget '.accessToken'  /  jget '.items[0].id'  (HTTP_BODY 기준, 없으면 빈 문자열)
JGET_PY='
import sys, json, re
expr = sys.argv[1].lstrip(".")
try:
    cur = json.loads(sys.stdin.read())
    for part in filter(None, expr.split(".")):
        m = re.match(r"^([^\[]*)((?:\[\d+\])*)$", part)
        if m.group(1):
            cur = cur[m.group(1)]
        for idx in re.findall(r"\[(\d+)\]", m.group(2)):
            cur = cur[int(idx)]
    out = "" if cur is None else (json.dumps(cur) if isinstance(cur, (dict, list)) else str(cur))
    sys.stdout.buffer.write(out.encode("utf-8"))
except Exception:
    pass
'
jget() {
  printf '%s' "$HTTP_BODY" | "$PY" -c "$JGET_PY" "$1" 2>/dev/null || true
}

# 응답 헤더 개수 (대소문자 무시)
header_count() { grep -ci "^$1:" "$TMP_HDR" 2>/dev/null || true; }

# ------------------------------------------------------------------ 단언
TC_FAILED=0
TC_SKIPPED=0
TC_MSG=""

TC_NOTE=""

fail() { TC_FAILED=1; TC_MSG="${TC_MSG:+$TC_MSG; }$*"; }
skip() { TC_SKIPPED=1; TC_MSG="$*"; }
note() { TC_NOTE="${TC_NOTE:+$TC_NOTE; }$*"; }   # 통과해도 리포트에 남기는 참고 값 (복구 시간 등)

expect_code() { # 기대코드(들: 200|201) [설명]
  local want="$1" label="${2:-}"
  case "|$want|" in
    *"|$HTTP_CODE|"*) return 0 ;;
  esac
  fail "${label:+$label: }HTTP 기대 $want 실제 $HTTP_CODE"
  return 1
}

expect_eq() { # 기대 실제 설명
  [ "$1" = "$2" ] && return 0
  fail "$3: 기대 '$1' 실제 '$2'"
  return 1
}

expect_contains() { # 문자열 설명   (HTTP_BODY 에 포함)
  case "$HTTP_BODY" in
    *"$1"*) return 0 ;;
  esac
  fail "$2: 응답에 '$1' 없음"
  return 1
}

expect_not_contains() {
  case "$HTTP_BODY" in
    *"$1"*) fail "$2: 응답에 '$1' 가 있으면 안 됨"; return 1 ;;
  esac
  return 0
}

# ------------------------------------------------------------------ 컨테이너 제어 (장애 시험)
need_docker() {
  if [ "$NO_DOCKER" = "1" ]; then
    skip "컨테이너 제어 불가 환경 (NO_DOCKER=1)"
    return 1
  fi
  return 0
}

compose() { $COMPOSE_CMD "$@"; }

# 게이트웨이 경유 /api/matches 가 200 으로 돌아올 때까지 (Eureka 갱신 포함) 대기. 초 반환(실패 시 -1)
wait_gateway_ok() { # 최대초
  local limit="${1:-120}" t0 t
  t0=$(date +%s)
  while :; do
    req GET /api/matches
    if [ "$HTTP_CODE" = "200" ]; then
      echo $(( $(date +%s) - t0 )); return 0
    fi
    t=$(( $(date +%s) - t0 ))
    [ "$t" -ge "$limit" ] && { echo -1; return 1; }
    sleep 3
  done
}

# 게이트웨이 경유 /api/matches 가 연속으로 200 인 시간이 안정초를 넘을 때까지 대기.
# match 재기동 직후에는 첫 200 뒤에도 게이트웨이 LoadBalancer 캐시(기본 TTL 35초)가 갱신될 때 인스턴스 목록이
# 잠깐 비어 "No servers available" 503 이 날 수 있다 (CI msa-tests 41772569367 의 TC-23). 그래서 TTL 보다 길게 본다.
wait_gateway_stable() { # [안정초 40] [최대초 150]
  local need="${1:-40}" limit="${2:-150}" t0 ok_since=""
  t0=$(date +%s)
  while [ $(( $(date +%s) - t0 )) -lt "$limit" ]; do
    req GET /api/matches
    if [ "$HTTP_CODE" = "200" ]; then
      [ -z "$ok_since" ] && ok_since=$(date +%s)
      [ $(( $(date +%s) - ok_since )) -ge "$need" ] && return 0
    else
      ok_since=""
    fi
    sleep 3
  done
  return 1
}

# 회원 서비스 라우트 복구 대기: 로그인 요청이 400/401(서비스가 응답)이 될 때까지. 503 이면 아직 라우팅 불가.
wait_user_route() { # [최대초]
  local limit="${1:-120}" t0
  t0=$(date +%s)
  while [ $(( $(date +%s) - t0 )) -lt "$limit" ]; do
    req POST /api/auth/login -d '{"email":"nobody@example.com","password":"x"}'
    case "$HTTP_CODE" in 400|401) return 0 ;; esac
    sleep 3
  done
  return 1
}

new_uuid() { "$PY" -c "import uuid; print(uuid.uuid4())"; }

# 내일 같은 시각 (이벤트 occurredAt 용: 컨테이너와 러너의 시간대가 달라도 기존 스냅샷보다 항상 늦다)
tomorrow_ts() {
  "$PY" -c "import datetime; print((datetime.datetime.now() + datetime.timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%S'))"
}

# DB 한 줄 조회: db_query <컨테이너> <계정> <DB> "<SQL>"  (헤더 없이 값만)
db_query() {
  compose exec -T "$1" mariadb -u"$2" -p"$2" "$3" -N -e "$4" 2>/dev/null | tr -d '\r'
}

wait_service_healthy() { # 서비스 [최대초]
  local svc="$1" limit="${2:-120}" t0 s
  t0=$(date +%s)
  while [ $(( $(date +%s) - t0 )) -lt "$limit" ]; do
    s="$(compose ps --all --format '{{.Service}} {{.State}} {{.Health}}' 2>/dev/null | awk -v s="$svc" '$1==s {print $2" "$3}')"
    [ "$s" = "running healthy" ] && return 0
    sleep 3
  done
  return 1
}

# ------------------------------------------------------------------ 데이터 만들기
# new_user 라벨 → 가입 + 로그인. U_EMAIL U_PW U_TOKEN U_REFRESH 설정
new_user() {
  local label="${1:-u}" stamp
  stamp="$(date +%s)${RANDOM}"
  U_EMAIL="t${label}${stamp}@example.com"
  U_PW="$PW"
  local nick="t${label}${stamp: -8}"
  req POST /api/users -d "{\"email\":\"$U_EMAIL\",\"password\":\"$U_PW\",\"gender\":\"MALE\",\"nickname\":\"$nick\",\"district\":\"GANGNAM\",\"sportTypes\":[\"FUTSAL\"]}"
  if [ "$HTTP_CODE" != "201" ]; then
    U_TOKEN=""; U_REFRESH=""
    fail "new_user 가입 실패 (HTTP $HTTP_CODE)"
    return 1
  fi
  req POST /api/auth/login -d "{\"email\":\"$U_EMAIL\",\"password\":\"$U_PW\"}"
  U_TOKEN="$(jget .accessToken)"
  U_REFRESH="$(jget .refreshToken)"
  if [ -z "$U_TOKEN" ]; then
    fail "new_user 로그인 실패 (HTTP $HTTP_CODE)"
    return 1
  fi
}

# 내일 모레 19:00 ~ 21:00 (GNU date, 없으면 python)
future_ts() { # 시작|종료
  "$PY" - "$1" <<'PYEOF'
import sys, datetime
d = datetime.datetime.now() + datetime.timedelta(days=2)
h = 19 if sys.argv[1] == "start" else 21
print(d.replace(hour=h, minute=0, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%S"))
PYEOF
}

# 매치 생성 (U_TOKEN 사용). 성공하면 MATCH_ID 설정
create_match() { # 제목 [종목 FUTSAL] [serviceId 1] [maxParticipant 6]
  local title="$1" sport="${2:-FUTSAL}" sid="${3:-1}" max="${4:-6}"
  req POST /api/matches -t "$U_TOKEN" -d "{\"serviceId\":$sid,\"title\":\"$title\",\"description\":\"msa pipeline test\",\"startAt\":\"$(future_ts start)\",\"endAt\":\"$(future_ts end)\",\"maxParticipant\":$max,\"skillLevel\":\"BEGINNER\",\"sportType\":\"$sport\",\"genderGroup\":\"MIXED\"}"
  # 생성 응답은 숫자 그대로(1) 이거나 {"matchId":1} 일 수 있다 (프론트도 둘 다 처리한다)
  MATCH_ID="$(jget .matchId)"
  if [ -z "$MATCH_ID" ] && [[ "$HTTP_BODY" =~ ^[0-9]+$ ]]; then MATCH_ID="$HTTP_BODY"; fi
  [ -n "$MATCH_ID" ]
}

# 장애 테스트(F 등) 직후에는 의존 서비스 회로가 아직 열려 있어 쓰기 요청이 잠시 5xx 일 수 있다.
# 사전 준비용 매치 생성만 최대 90초 재시도한다 (검증 대상 요청에는 쓰지 않는다). 기다린 시간은 비고에 남는다.
create_match_ready() { # create_match 와 같은 인자
  local waited=0
  until create_match "$@"; do
    case "$HTTP_CODE" in 5??|000) ;; *) return 1 ;; esac
    [ "$waited" -ge 90 ] && return 1
    sleep 5; waited=$((waited + 5))
  done
  [ "$waited" -gt 0 ] && note "사전 매치 생성이 ${waited}초 뒤에 가능 (앞선 장애 테스트의 회복 대기)"
  return 0
}

# ------------------------------------------------------------------ 결과 기록
record() { # 스위트 점수 참조 이름 상태 메시지 대기 초
  mkdir -p "$RESULTS_DIR"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$5" "$6" "$7" "$8" >> "$RESULTS_DIR/results.tsv"
}

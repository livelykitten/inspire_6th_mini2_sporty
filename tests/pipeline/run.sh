#!/usr/bin/env bash
# MSA 테스트 파이프라인 러너 (이슈 #43). 스위트 8개 · 테스트 29개 · 100점.
#
#   bash tests/pipeline/run.sh                    # 전체 (기본 BASE=http://localhost:8000)
#   bash tests/pipeline/run.sh --suite A,B,C      # 일부 스위트만
#   BASE=http://<ALB> NO_DOCKER=1 bash tests/pipeline/run.sh   # 컨테이너를 제어할 수 없는 환경 (배포 주소 점검)
#   MIN_SCORE=60 bash tests/pipeline/run.sh       # 합격선 미만이면 종료 코드 1
#
# 결과: tests/pipeline/results/{results.tsv, results.json, summary.md}
# 담당: 반정욱 (@salieri009) · 작업 위치: tests/pipeline/run.sh
set -u
cd "$(dirname "$0")/../.."

SELECT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --suite) SELECT="$2"; shift 2 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done

# shellcheck source=lib.sh
. tests/pipeline/lib.sh

rm -rf "$RESULTS_DIR"; mkdir -p "$RESULTS_DIR"

for f in tests/pipeline/suites/*.sh; do
  # shellcheck disable=SC1090
  . "$f"
done

echo "[pipeline] BASE=$BASE  docker=$([ "$NO_DOCKER" = "1" ] && echo off || echo on)  suites=${SELECT:-all}"

selected() { [ -z "$SELECT" ] && return 0; case ",$SELECT," in *",$1,"*) return 0 ;; esac; return 1; }

# 테스트 하나가 멈춰도 전체가 멈추지 않도록 시간 제한을 둔다 (기본 420초, TC_TIMEOUT 으로 변경).
# 테스트는 서브셸에서 돌고 결과(TC_*)는 파일로 돌려받는다. 시간이 지나면 FAIL 로 기록하고 멈춰 있던 컨테이너를 되살린다.
run_with_timeout() { # 함수
  local fn="$1" limit="${TC_TIMEOUT:-420}" state="$TMP_DIR/state"
  : > "$state"
  (
    "$fn" || true
    printf 'TC_FAILED=%s
TC_SKIPPED=%s
TC_MSG=%q
TC_NOTE=%q
' "$TC_FAILED" "$TC_SKIPPED" "$TC_MSG" "$TC_NOTE" > "$state"
  ) &
  local pid=$!
  ( sleep "$limit"; kill -9 "$pid" 2>/dev/null ) &
  local watcher=$!
  wait "$pid" 2>/dev/null
  kill "$watcher" 2>/dev/null; wait "$watcher" 2>/dev/null
  if [ -s "$state" ]; then
    # shellcheck disable=SC1090
    . "$state"
  else
    TC_FAILED=1
    TC_MSG="시간 초과(${limit}초) 또는 비정상 종료 - 테스트가 멈췄다"
    if [ "$NO_DOCKER" != "1" ]; then compose up -d >/dev/null 2>&1 || true; fi
  fi
}

for entry in "${TC_LIST[@]}"; do
  IFS='|' read -r sid points ref name func needs <<<"$entry"
  selected "$sid" || continue
  TC_FAILED=0; TC_SKIPPED=0; TC_MSG=""; TC_NOTE=""
  t0=$(date +%s)
  run_with_timeout "$func"
  secs=$(( $(date +%s) - t0 ))
  if [ "$TC_SKIPPED" = "1" ]; then status=SKIP; msg="$TC_MSG"
  elif [ "$TC_FAILED" = "1" ]; then status=FAIL; msg="$TC_MSG"
  else status=PASS; msg="$TC_NOTE"
  fi
  record "$sid" "$points" "$ref" "$name" "$status" "$msg" "$needs" "$secs"
  printf '  [%s] %-4s %-2s점  %-34s %s  (%ss)%s\n' "$sid" "$status" "$points" "$ref" "$name" "$secs" "${msg:+  - $msg}"
done

# 스위트 메타데이터 (report.py 가 읽는다)
for s in "${SUITE_LIST[@]}"; do
  printf '%s\n' "$s" >> "$RESULTS_DIR/suites.txt"
done

"$PY" tests/pipeline/report.py "$RESULTS_DIR" "${MIN_SCORE:-0}"

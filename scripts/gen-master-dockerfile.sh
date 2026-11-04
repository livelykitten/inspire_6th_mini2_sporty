#!/usr/bin/env bash
# master Dockerfile(backend/Dockerfile) 생성기.
#
# 원본은 backend/{서비스}/Dockerfile 7개이고 master 는 그것을 이어 붙인 생성물이다 (손으로 고치지 않는다).
# 담당자는 자기 서비스 Dockerfile 만 고친 뒤 이 스크립트를 실행해 master 를 함께 커밋한다.
#
#   bash scripts/gen-master-dockerfile.sh          master 를 다시 만든다
#   bash scripts/gen-master-dockerfile.sh --check  master 가 최신인지만 확인한다 (CI 에서 사용, 다르면 실패)
#
# master 는 컨테이너를 실행하지 않는다. `--target {서비스}` 로 서비스 이미지를 뽑는 빌드 진입점이고,
# 실제 기동은 compose.app.yaml 이 target 별로 한다.
#   docker build --target user-service -t sporty/user-service:local backend
#   (config-service 는 --build-context config-repo=config-repo 가 추가로 필요하다)
set -euo pipefail
cd "$(dirname "$0")/.."

SERVICES=(discovery-service config-service gateway-service user-service match-service facility-service ai-service notification-service)
OUT=backend/Dockerfile

generate() {
  echo "# syntax=docker/dockerfile:1"
  echo "# =============================================================================="
  echo "# 생성물 - 직접 수정 금지. 원본은 backend/{서비스}/Dockerfile 이다."
  echo "# 담당: 반정욱 (@salieri009) · 재생성: bash scripts/gen-master-dockerfile.sh"
  echo "# 사용: docker build --target {서비스} -t sporty/{서비스}:local backend"
  echo "# =============================================================================="
  for s in "${SERVICES[@]}"; do
    f="backend/$s/Dockerfile"
    [ -f "$f" ] || { echo "없는 파일: $f" >&2; exit 1; }
    owner=$(tr -d '\r' <"$f" | grep -m1 '^# 담당:' | sed -e 's/^# 담당: //' -e 's/ · 리뷰:.*//')
    echo
    echo "# ----- [$s] 담당: $owner · 원본: $f -----"
    tr -d '\r' <"$f" \
      | grep -v '^# syntax=' \
      | sed -e "s/ AS build\$/ AS $s-build/" \
            -e "s/ AS runtime\$/ AS $s/" \
            -e "s/--from=build /--from=$s-build /"
  done
}

if [ "${1:-}" = "--check" ]; then
  tmp=$(mktemp)
  trap 'rm -f "$tmp"' EXIT
  generate >"$tmp"
  if ! diff -u <(tr -d '\r' <"$OUT") "$tmp"; then
    echo >&2
    echo "backend/Dockerfile(master) 이 서비스별 Dockerfile 과 다릅니다. 'bash scripts/gen-master-dockerfile.sh' 를 실행해 master 를 같이 커밋하세요." >&2
    exit 1
  fi
  echo "master Dockerfile 최신"
else
  generate >"$OUT"
  echo "생성: $OUT ($(grep -c '^FROM ' "$OUT") stage)"
fi

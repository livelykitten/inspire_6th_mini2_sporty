#!/usr/bin/env bash
# Git Bash, repository root: bash scripts/integration/w17-em03-fallback.sh [MATCH_ID]
set -euo pipefail

# Find the repository root even when invoked from scripts/integration/.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$ROOT/compose.yaml" ]]; then
  cd "$ROOT"
elif [[ -f "$ROOT/../../compose.yaml" ]]; then
  cd "$ROOT/../.."
else
  echo '오류: 스크립트를 저장소 루트 또는 scripts/integration/에 놓아주세요.' >&2
  exit 1
fi

MATCH_ID="${1:-}"
if [[ -z "$MATCH_ID" ]]; then
  echo 'match-db에서 참가자가 있는 매치를 찾는 중...'
  MATCH_ID="$(docker compose exec -T match-db mariadb -umatch_svc -pmatch_svc match_db -N -B \
    -e 'SELECT match_id FROM match_participant GROUP BY match_id ORDER BY COUNT(*) DESC, match_id LIMIT 1;' \
    2>/dev/null | tr -d '\r' | head -n 1)"
fi
if [[ ! "$MATCH_ID" =~ ^[0-9]+$ ]]; then
  echo '오류: 참가자가 있는 매치를 찾지 못했습니다. DB 내용을 확인하거나 매치 ID를 인자로 전달하세요.' >&2
  exit 1
fi

URL="http://localhost:8002/api/matches/$MATCH_ID"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fetch() {
  curl.exe --silent --show-error --output "$1" --write-out '%{http_code}' "$URL" | tr -d '\r'
}

printf '\n[1/2] 정상 응답: user-service 실행 중이어야 합니다 (matchId=%s)\n' "$MATCH_ID"
BEFORE_STATUS="$(fetch "$TMP/before.json")"
if [[ "$BEFORE_STATUS" != 200 ]]; then
  echo "실패: 정상 응답 HTTP $BEFORE_STATUS"; cat "$TMP/before.json"; echo; exit 1
fi
BEFORE_COUNT="$(grep -oE '"currentParticipantCount":[0-9]+' "$TMP/before.json" | cut -d: -f2 || true)"
if [[ -z "$BEFORE_COUNT" || "$BEFORE_COUNT" == 0 ]] || ! grep -qE '"profileId":[0-9]+' "$TMP/before.json"; then
  echo '오류: 참가자와 실제 프로필이 있는 매치가 필요합니다.'; cat "$TMP/before.json"; echo; exit 1
fi
echo "정상: HTTP 200, 참가자 $BEFORE_COUNT명, 프로필 존재"

printf '\n지금 user-service 프로세스를 종료하세요. match-service는 켜 두세요.\n'
read -r -p '종료했으면 Enter를 누르세요... ' _

printf '\n[2/2] 장애 응답 (user-service 중단 상태)\n'
AFTER_STATUS="$(fetch "$TMP/after.json")"
cat "$TMP/after.json"; echo
if [[ "$AFTER_STATUS" != 200 ]]; then
  echo "실패: HTTP $AFTER_STATUS (기대: 200)"; exit 1
fi
AFTER_COUNT="$(grep -oE '"currentParticipantCount":[0-9]+' "$TMP/after.json" | cut -d: -f2 || true)"
NULL_PROFILE_COUNT="$({ grep -o '"profileId":null' "$TMP/after.json" || true; } | wc -l | tr -d '[:space:]')"
NULL_NICK_COUNT="$({ grep -o '"nickname":null' "$TMP/after.json" || true; } | wc -l | tr -d '[:space:]')"
NULL_IMAGE_COUNT="$({ grep -o '"imageUrl":null' "$TMP/after.json" || true; } | wc -l | tr -d '[:space:]')"
ROLE_COUNT="$({ grep -oE '"role":"[^"]+"' "$TMP/after.json" || true; } | wc -l | tr -d '[:space:]')"
if [[ "$AFTER_COUNT" == "$BEFORE_COUNT" && "$NULL_PROFILE_COUNT" == "$BEFORE_COUNT" && \
      "$NULL_NICK_COUNT" == "$BEFORE_COUNT" && "$NULL_IMAGE_COUNT" == "$BEFORE_COUNT" && \
      "$ROLE_COUNT" == "$BEFORE_COUNT" ]]; then
  echo "PASS: HTTP 200, 참가자 ${AFTER_COUNT}명 유지, 모든 프로필 필드 null, 역할 유지"
else
  echo "FAIL: 인원 before=$BEFORE_COUNT after=$AFTER_COUNT / null 프로필=$NULL_PROFILE_COUNT / null 닉네임=$NULL_NICK_COUNT / null 이미지=$NULL_IMAGE_COUNT / 역할=$ROLE_COUNT"
  exit 1
fi

#!/usr/bin/env bash
# Sporty 를 Docker Desktop Kubernetes 에 올린다.
#   bash k8s/up.sh            # 이미지 빌드 + 배포 + 대기
#   bash k8s/up.sh --no-build # 이미지가 이미 있으면 빌드 생략
# 저장소 루트의 .env 를 읽는다 (cp .env.example .env). 필수 = JWT_SECRET_KEY · ENCRYPT_KEY
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
K8S="$ROOT/k8s"
ENV_FILE="$ROOT/.env"
NS=sporty

ctx="$(kubectl config current-context 2>/dev/null || true)"
if [ "$ctx" != "docker-desktop" ]; then
  echo "kubectl 컨텍스트가 docker-desktop 이 아니다 (지금: ${ctx:-없음})."
  echo "Docker Desktop > Settings > Kubernetes > Enable Kubernetes 를 켠 뒤: kubectl config use-context docker-desktop"
  exit 1
fi

[ -f "$ENV_FILE" ] || { echo ".env 가 없다. 저장소 루트에서: cp .env.example .env"; exit 1; }

# .env 에서 KEY=VALUE 한 줄을 읽는다 (따옴표 제거, 마지막 값 우선)
get() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//" || true; }

JWT="$(get JWT_SECRET_KEY)"; ENC="$(get ENCRYPT_KEY)"
OAI="$(get OPEN_AI_KEY)"; PDK="$(get PUBLIC_DATA_API_KEY)"; PDU="$(get PUBLIC_DATA_API_URL)"
[ -n "$JWT" ] || { echo ".env 에 JWT_SECRET_KEY 가 없다"; exit 1; }
[ -n "$ENC" ] || { echo ".env 에 ENCRYPT_KEY 가 없다 (없으면 gateway 가 jwt.secret 을 못 받는다)"; exit 1; }
[ -n "$OAI" ] || { OAI="not-set-dummy-key"; echo "OPEN_AI_KEY 없음 → 더미 값(서비스는 뜨고 AI 호출만 실패)"; }
# 서울시 공공데이터: 키가 있을 때만 Secret 에 넣는다. 주소가 비면 서울시 기본 주소(backend/facility-service/.env.example 과 같은 값)
PD_ARGS=()
if [ -n "$PDK" ]; then
  [ -n "$PDU" ] || PDU="http://openapi.seoul.go.kr:8088/"
  PD_ARGS=(--from-literal=PUBLIC_DATA_API_KEY="$PDK" --from-literal=PUBLIC_DATA_API_URL="$PDU")
else
  echo "PUBLIC_DATA_API_KEY 없음 → 시설 동기화 실패(시설 0건, 매치 생성 불가). 키는 루트 .env 에"
fi

if [ "${1:-}" != "--no-build" ]; then
  echo "== 이미지 빌드 (compose 와 같은 sporty/*:local)"
  (cd "$ROOT" && docker compose -f compose.yaml -f compose.app.yaml build)
fi

echo "== 네임스페이스 · 비밀값"
kubectl apply -f "$K8S/00-namespace.yaml"
kubectl -n "$NS" create secret generic sporty-secrets \
  --from-literal=JWT_SECRET_KEY="$JWT" \
  --from-literal=ENCRYPT_KEY="$ENC" \
  --from-literal=OPEN_AI_KEY="$OAI" \
  "${PD_ARGS[@]}" \
  --dry-run=client -o yaml | kubectl apply -f -

had_facility="$(kubectl -n "$NS" get deploy facility-service --ignore-not-found -o name)"

echo "== 배포"
for f in 10-infra 20-config 30-platform 40-services 50-edge; do
  kubectl apply -f "$K8S/$f.yaml"
done

# Secret 값은 파드가 뜰 때만 읽는다. 이미 떠 있던 facility 는 새 키를 읽도록 다시 띄운다
if [ -n "$had_facility" ] && [ "${#PD_ARGS[@]}" -gt 0 ]; then
  echo "== 서울시 키 반영: facility-service 재시작"
  kubectl -n "$NS" rollout restart deploy/facility-service
fi

echo "== 기동 대기 (최대 15분. 다른 창에서 kubectl -n sporty get pods -w 로 볼 수 있다)"
kubectl -n "$NS" wait --for=condition=available deployment --all --timeout=15m
kubectl -n "$NS" wait --for=condition=complete job/kafka-init --timeout=5m || true

kubectl -n "$NS" get pods -o wide
kubectl -n "$NS" get svc
echo
echo "프론트   http://localhost:8080"
echo "게이트웨이 http://localhost:8000/actuator/health"
echo "Eureka   http://localhost:8761"
echo "Zipkin   http://localhost:9411"
echo "확인: bash k8s/verify.sh"

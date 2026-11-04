#!/usr/bin/env bash
# 배포 확인: 파드 상태 · 진입점 응답 · Eureka 등록 수. 증빙 캡처 전에 한 번 돌린다.
set -uo pipefail
NS=sporty
pass=0; fail=0
ok()  { echo "PASS  $1"; pass=$((pass+1)); }
ng()  { echo "FAIL  $1"; fail=$((fail+1)); }

echo "== 파드"
kubectl -n "$NS" get pods
notready="$(kubectl -n "$NS" get pods --no-headers | grep -v -E 'Running|Completed' | wc -l | tr -d ' ')"
[ "$notready" = "0" ] && ok "파드 전부 Running(kafka-init 은 Completed)" || ng "Running 이 아닌 파드 ${notready}개"

code() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$1"; }
[ "$(code http://localhost:8080/healthz)" = "200" ] && ok "프론트 nginx localhost:8080" || ng "프론트 nginx localhost:8080"
[ "$(code http://localhost:8000/actuator/health)" = "200" ] && ok "게이트웨이 localhost:8000" || ng "게이트웨이 localhost:8000"
# 기동 직후 30 ~ 60초는 게이트웨이의 Eureka 캐시 때문에 503 이 나온다 → 200 이 될 때까지 최대 90초 다시 본다
m="000"; for i in $(seq 1 18); do m="$(code http://localhost:8080/api/matches)"; [ "$m" = "200" ] && break; sleep 5; done
[ "$m" = "200" ] && ok "프론트 → 게이트웨이 → 매치 서비스 (/api/matches 200)" || ng "프론트 → 게이트웨이 → 매치 서비스 (/api/matches $m)"
f="$(code 'http://localhost:8080/api/services?page=0&size=1')"
[ "$f" = "200" ] && ok "프론트 → 게이트웨이 → 시설 서비스 (/api/services 200)" || ng "프론트 → 게이트웨이 → 시설 서비스 (/api/services $f)"
# 시설이 0건이면 매치를 만들 수 없다(시연 흐름 불가). 실패로 세지 않고 알려만 준다
n="$(curl -s --max-time 10 'http://localhost:8080/api/services?page=0&size=1' | grep -c '"serviceId"' || true)"
[ "${n:-0}" -ge 1 ] && echo "INFO  시설 데이터 있음" || echo "INFO  시설 0건 → 루트 .env 에 PUBLIC_DATA_API_KEY 를 넣고 bash k8s/up.sh --no-build 다시 실행"

# 파드가 Ready 여도 Eureka 등록은 몇 초 늦을 수 있다 → 6개가 될 때까지 최대 60초
for i in $(seq 1 12); do
  apps="$(curl -s --max-time 10 -H 'Accept: application/json' http://localhost:8761/eureka/apps | grep -o '"name":"[A-Z-]*"' | sort -u | wc -l | tr -d ' ')"
  [ "${apps:-0}" -ge 6 ] && break; sleep 5
done
[ "${apps:-0}" -ge 6 ] && ok "Eureka 등록 서비스 ${apps}개" || ng "Eureka 등록 서비스 ${apps:-0}개 (6개 기대: config · user · match · facility · ai · gateway)"

echo
echo "결과: PASS $pass · FAIL $fail"
echo "다음: 브라우저 http://localhost:8080 에서 회원가입 → 로그인 → 매치 목록, 그리고 캡처 3장(kubectl get pods · Eureka · 매치 목록 화면)"
[ "$fail" = "0" ]

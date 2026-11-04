# W-13 첫 통합 확인 (프론트 → 8000 → 로그인 · 목록 · 상세)

로컬 개발용 기록(더미 데이터·계정은 운영에 쓰지 않는다). 1차는 `origin/develop`(1d8c07c) + `feat/gateway-w12-auth` 로컬 머지(PR #6 미머지 상태) 기준으로 2026-11-02 에 실행했고, 최종 기준은 맨 아래 **3차 확인**(`origin/develop` 79011e7, 2026-11-03)이다.

## 실행 순서

```bash
docker compose up -d --wait                       # user-db 3306 · match-db 3307 · facility-db 3308 · redis · kafka
cd backend && ./gradlew :discovery-service:bootJar :gateway-service:bootJar :user-service:bootJar \
                       :match-service:bootJar :facility-service:bootJar -x test
# backend/.env (커밋 금지) — JWT_SECRET_KEY, jwt.secret(facility 1차 필터용), facility.openapi.* 더미
# 각 서비스 폴더에서 java -jar build/libs/*.jar (discovery 먼저)
bash scripts/w13-seed.sh      # 시설 2건 · 스냅샷 SQL, 회원 2 · 매치 2 는 게이트웨이 API
bash scripts/w13-verify.sh    # 아래 표
```

## 결과 (curl, 게이트웨이 :8000 경유) — 17/17 통과

| 확인 | 기대 | 결과 |
|---|---|---|
| 로그인 → accessToken 발급 | 200 | PASS |
| 토큰 없이 `GET /api/users/me` | 401 | PASS |
| 토큰으로 `GET /api/users/me` | 200 | PASS |
| `X-User-Id: 999` 만 | 401 | PASS |
| 토큰 + `X-User-Id: 999` | 200 (토큰 sub 로 덮어씀) | PASS |
| `GET /api/matches` 비로그인 | 200 | PASS |
| 매치 상세 비로그인 / 틀린 토큰 | 200 / 200 | PASS |
| 없는 매치 상세 | 404 | PASS |
| `GET /api/matches/me` 토큰 없이 / 있음 | 401 / 200 | PASS |
| `GET /api/services`, `/api/services/1` | 200 | PASS |
| `POST /api/matches` 토큰 없이 | 401 | PASS |
| 리프레시 토큰으로 보호 경로 | 401 | PASS |
| `POST /api/auth/refresh` | 200 | PASS |
| `GET /internal/members/1` | 404 | PASS |
| CORS (`Origin: http://localhost:3000`) | 허용 헤더 1회 | PASS (preflight · 401 응답 모두) |

## 브라우저(프론트) 확인 — 통과

프론트 `http://localhost:3001` (`REACT_APP_BACKEND_ENDPOINT=http://localhost:8000`), 게이트웨이는 `CORS_ALLOWED_ORIGIN=http://localhost:3001` 로 기동. 3000 포트는 관리자 권한으로 도는 Grafana 가 써서 3001 로 확인했다 (환경변수 한 줄로 오리진을 바꿀 수 있다는 것도 함께 검증).

| 화면 | 네트워크 (모두 `:8000`) | 결과 |
|---|---|---|
| 로그인 (`w13a@example.com`) | `POST /api/auth/login` → 홈으로 이동, `GET /api/users/me` 200 | PASS |
| 매치 목록 `/matches/search` | `GET /api/matches` 200, 카드 2개 (풋살 · 농구) | PASS |
| 매치 상세 `/matches/1` | `GET /api/matches/1` 200, 일시 · 종목 · 시설(더미 풋살장 예약) · 인원 1/6 표시, 생성자라 삭제/수정 버튼 노출 | PASS |
| CORS | 모든 요청에 OPTIONS 200 선행, 콘솔 CORS 오류 없음 | PASS |

화면 밖 관찰:

- 홈의 AI 추천 호출 `GET /api/ai/matches/recommendations` 는 503 — ai-service(W-10)가 아직 없어서 정상.
- 매치 상세의 "매치 생성자" 칸이 "프로필 정보 없음 / 개설자" — match-service 의 임시 회원 조회(`TemporaryMemberLookup`)라서. user-service Feign(W-17 ~ W-20) 연결 뒤 채워져야 한다.

## 발견한 문제 (담당 전달)

1. **actuator 헬스 401** — user · match · facility 의 `/actuator/health` 가 401 (서비스 SecurityConfig 가 막음). ALB 헬스체크(W-29/35)와 compose `depends_on` healthcheck(W-34)가 통과하지 못한다. 각 서비스 permitAll 필요 (user W-07/11, match W-08, facility W-09).
2. **facility-service 가 develop 에서 기동 안 됨** — 1차 `jwtAuthenticationFilter` 가 `jwt.secret` 을 요구하고 `facility.openapi.*` 도 필수. W-09(#11) 머지 전이라 임시로 `backend/.env` 에 값을 넣어 기동했다.
3. **match 시설 스냅샷** — 매치 개설은 `facility_service_snapshot` 이 있어야 하는데 채우는 Kafka 동기화(W-18/19)가 아직 없어 스크립트가 SQL 로 넣는다.
4. **한글 JSON** — Git Bash 의 curl 로 한글 본문을 보내면 UTF-8 이 아니라 400 (`Invalid UTF-8 start byte`). 서비스 문제는 아니다 (python UTF-8 요청은 201). 스크립트는 영문 값을 쓴다.
5. 8000 포트를 예전 모놀리스 백엔드가 쓰고 있어 게이트웨이가 못 떴다 (종료 후 해결). 팀원이 1차 앱을 켜 둔 상태면 같은 증상이 난다.

## 2차 확인 (develop bd2bfe2 + PR #11 · #15 · #16 로컬 머지, 2026-11-02)

- 환경: `.env.example` 를 `backend/.env` 로 복사만 함 (임시 `jwt.secret` · `facility.openapi.*` 제거). 서비스 5개 + config-service 기동.
- 검증 스크립트 17/17 통과. gateway 로그에 401 · 404(`/internal/**`, 라우트 없는 경로) 기록 확인 (#15).
- facility: `/actuator/health` 200 (user · match 는 여전히 401). 없는 경로 404 · 잘못된 메서드 405 (#11 반영).
- facility 기동 시 동기화는 `PUBLIC_DATA_API_KEY` 가 비어 실패하지만 서비스는 계속 뜸. `ddl-auto: create-drop` 이라 재기동 때마다 시설 0건에서 시작.
- config-service: `POST /encrypt` 200 (#16).
- 막힌 것: `match-service` 테스트 컴파일 실패(#18), `ai-service` 컴파일 실패 (W-10 미착수, PR 없음).

## 3차 확인 (origin/develop 79011e7 = PR #22 까지 포함, 2026-11-03)

- 환경: 로컬 머지 없이 `origin/develop` 그대로. `.env.example` 를 `backend/.env` 로 복사, compose 인프라(DB 3 · Redis · Kafka) + discovery · config · gateway · user · match · facility 기동. 게이트웨이는 `CORS_ALLOWED_ORIGIN=http://localhost:3001`.
- `bash scripts/w13-verify.sh` **17/17 PASS**. (마지막 CORS 항목은 스크립트가 오리진 3000 으로 확인하므로 위 환경에서는 403 이 나온다. 3001 오리진으로 직접 확인: preflight 200, 응답 `Access-Control-Allow-Origin: http://localhost:3001` 1회, 401 응답에도 동일.)
- 시드 스크립트는 재실행해도 안전하다 (회원 가입 409 는 무시, 시설은 `ON DUPLICATE KEY UPDATE`). 단 매치는 실행할 때마다 새로 개설된다.

| 확인 | 2차 | 3차 |
|---|---|---|
| user-service `/actuator/health` | 401 | **401 (미해결)** |
| match-service `/actuator/health` | 401 | 200 (#19) |
| facility-service `/actuator/health` | 200 | 200 |
| match-service 테스트 컴파일 | 실패 | 해결 (#21) |
| facility 기동 (`.env.example` 만) | 기동됨 | 기동됨 |
| `ai-service` | 컴파일 실패 | PR #24 (`fa643ff`) 로 빌드·기동 가능 |

### PR #24 (ai-service) 경유 확인 — 게이트웨이 8000 → 8004

- `ai-service` 를 PR #24 `fa643ff` 로 빌드해 기동 → Eureka 에 `AI-SERVICE` 등록. 게이트웨이 Eureka 캐시 반영까지 약 1분 걸려 그 사이는 503.
- 토큰 없이 `GET /api/ai/matches/recommendations` → 401.
- w13a(두 매치의 개설자)로 호출 → 200 `[]`. 로그 `candidates : 0` — IN-04 로 내 매치를 후보에서 제외한 결과.
- w13b(개설자 아님)로 호출 → 후보 3건 조회됨(IN-03 · IN-05 Feign 성공) 후 OpenAI 호출에서 500 (`.env` 의 `OPEN_AI_KEY` 가 더미라서). 서비스 결함이 아니라 키 없는 환경의 한계이며, OpenAI 구간은 이 검증에 포함되지 않는다.
- `ai-service` `/actuator/health` 401 (user-service 와 같은 문제).

### 브라우저(프론트) 확인

3차에서는 브라우저 확장이 연결되지 않아 화면 3종(로그인 · 목록 · 상세) 재확인을 하지 못했다. 위 "브라우저(프론트) 확인" 은 1차(2026-11-02) 결과다. 3차 기준 화면 확인과 증빙 캡처는 PR 머지 전에 한 번 더 한다.

### 남은 문제

1. **`/actuator/health` 401** — user-service · ai-service. 각 서비스 permitAll 필요 (user 송석주, ai 서정운; PR #24 에 변경 요청함).
2. 매치 상세의 "매치 생성자" 임시 표시(`TemporaryMemberLookup`)는 W-17 연결 전까지 유지.
3. 시설 스냅샷은 Kafka 동기화(W-18/19) 전까지 시드 스크립트가 SQL 로 채움.

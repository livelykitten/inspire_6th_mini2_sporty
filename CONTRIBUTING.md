# 기여 가이드

## 구조

`backend/` 아래에 서비스 7개가 하나의 Gradle 멀티프로젝트로 있다. 기존 모놀리스 `backend/sporty` 는 서비스 코드 이동(W-07 ~ W-10)이 끝날 때까지 그대로 둔다.

| 서비스 | 포트 |
|---|---|
| gateway-service | 8000 |
| discovery-service | 8761 |
| config-service | 8888 |
| user-service | 8001 |
| match-service | 8002 |
| facility-service | 8003 |
| ai-service | 8004 |

```bash
cd backend
./gradlew.bat build -x test              # 전체 빌드
./gradlew.bat :user-service:bootRun      # 서비스 하나 실행
```

## 작업 흐름

통합 브랜치는 `develop` 이다. `main` 은 제출·배포용이라 `develop` 에서만 올린다.

1. 작업 1건 = 브랜치 1개: `develop` 에서 `feat/{서비스}-{작업}` 으로 분기한다 (예: `feat/user-w07-code-move`, 버그는 `fix/...`).
2. 커밋 메시지는 `[W-xx] 한 줄 요약` 으로 시작한다. 왜 바꿨는지는 본문에 쓴다.
3. PR 은 `develop` 으로 올리고 제목은 `[W-xx] 제목` 이다. 본문은 PR 템플릿을 채우고 **AI 사용 여부를 한 줄** 적는다.
4. CODEOWNERS 로 자동 지정된 리뷰어 중 승인 1명 이상 후 머지한다. 자기 PR 은 자기가 승인하지 않는다.
5. 머지 후 증빙 이미지를 증빙 탭에 올리고 시트 WBS 상태를 "완료"로 바꾼다 (완료 = PR 머지 + 증빙).
6. 명세 변경은 상대 담당 + 민경완 합의 → 팀 시트 수정 → 디코 #명세-변경 순서로 한다.

## CI

PR 과 `develop`/`main` push 마다 [.github/workflows/ci.yml](.github/workflows/ci.yml) 이 돈다.

- backend: 서비스별로 `compileJava`, `compileTestJava`. 아직 경계 import 가 남은 서비스(user · match · ai)는 `wip: true` 라 실패해도 PR 이 막히지 않는다. 자기 서비스 이동 PR 이 컴파일에 성공하면 그 줄의 `wip` 를 `false` 로 바꾼다.
- frontend: `npm ci` → `npm test` → `npm run build`.

로컬에서 같은 확인:

```bash
cd backend && ./gradlew.bat :facility-service:compileJava :facility-service:compileTestJava
cd frontend && npm ci && CI=true npm test -- --watchAll=false && npm run build
```

## 규칙

- 서비스는 자기 DB 만 읽고 쓴다. 다른 서비스 데이터는 조회 = Feign, 복제 · 상태 전파 = Kafka 로만 가져온다.
- 서비스 간 공통 모듈은 두지 않는다. 응답 DTO 는 호출하는 서비스에 복사한다.
- 비밀값(`.env`, API 키)은 커밋하지 않는다. 예시 값은 `.env.example` 에만 둔다.
- Compose · Dockerfile · 공용 Gradle 설정 · `.github/` 변경은 반정욱이 리뷰한다 (CODEOWNERS).

## 로컬 인프라 (W-06)

`compose.yaml` 이 서비스당 MariaDB 1대, Redis, Kafka(KRaft 단일 노드)를 띄운다. 값은 기본값만으로 동작하고, 바꾸려면 `.env.example` 을 `.env` 로 복사한다.

| 컨테이너 | 호스트 포트 | 컨테이너 안 주소 | DB / 계정 |
|---|---|---|---|
| user-db | 3306 | `user-db:3306` | `user_db` / `user_svc` |
| match-db | 3307 | `match-db:3306` | `match_db` / `match_svc` |
| facility-db | 3308 | `facility-db:3306` | `facility_db` / `facility_svc` |
| redis | 6379 | `redis:6379` | user 전용 refresh token |
| kafka | 9092 | `kafka:29092` | 토픽 4개 + 각 `.DLT` 토픽 자동 생성 |

```bash
docker compose up -d --wait              # 기동 (kafka-init 이 토픽을 만들고 종료한다)
docker compose ps
docker compose exec match-db mariadb -umatch_svc -pmatch_svc match_db   # DB 접속
docker compose down                      # 중지 (데이터 유지)
docker compose down -v                   # 중지 + 데이터 초기화
```

서비스 계정은 자기 DB 에만 권한이 있고 DB 가 서로 다른 인스턴스라, 다른 서비스 DB 에는 접속 자체가 되지 않는다 (MSA-08 증빙).

## 서비스 이미지 빌드 (W-23)

서비스마다 자기 Dockerfile 을 가진다 (멀티스테이지: JDK 17 로 `bootJar` → JRE 17 비루트 실행). 빌드 컨텍스트는 항상 `backend/` 이다 (`settings.gradle` 이 7개 모듈을 모두 include 하기 때문).

| 서비스 | 포트 | 담당 · 작업 위치 (원본) |
|---|---|---|
| gateway-service | 8000 | 김우석 · `backend/gateway-service/Dockerfile` |
| discovery-service | 8761 | 김우석 · `backend/discovery-service/Dockerfile` |
| config-service | 8888 | 김우석 · `backend/config-service/Dockerfile` |
| user-service | 8001 | 송석주 · `backend/user-service/Dockerfile` |
| match-service | 8002 | 김예림 · `backend/match-service/Dockerfile` |
| facility-service | 8003 | 이서현 · `backend/facility-service/Dockerfile` |
| ai-service | 8004 | 서정운 · `backend/ai-service/Dockerfile` |

```bash
docker build -f backend/user-service/Dockerfile -t sporty/user-service:local backend
# config-service 만 저장소 루트의 config-repo/ 가 필요해서 named context 를 하나 더 준다
docker build -f backend/config-service/Dockerfile --build-context config-repo=config-repo -t sporty/config-service:local backend
```

- **담당자는 자기 서비스 Dockerfile 만 고친다.** 각 파일 맨 위 주석에 담당자 · 작업 위치 · 빌드 명령이 적혀 있다. 브랜치는 `feat/{서비스}-…`, 리뷰어는 CODEOWNERS 로 담당자 + 반정욱이 자동 지정된다.
- **master(`backend/Dockerfile`)는 생성물이라 직접 고치지 않는다.** 서비스별 Dockerfile 7개를 이어 붙인 파일이고, 서비스 Dockerfile 을 고친 뒤 `bash scripts/gen-master-dockerfile.sh` 를 실행해 같이 커밋한다 (CI 가 `--check` 로 최신 여부를 검사한다).
- master 는 컨테이너를 실행하지 않는다. `--target {서비스}` 로 이미지를 뽑는 진입점이고, 전체를 한 번에 띄우는 것은 아래 앱 Compose 가 한다.
- ai-service 는 PR #24 가 머지되기 전까지 컴파일되지 않아 이미지가 만들어지지 않는다.
- 이미지 안에는 `.env` 를 넣지 않는다 (`backend/.dockerignore`). 설정은 환경변수로 넘긴다.
- JVM 메모리는 `JAVA_TOOL_OPTIONS` 의 `MaxRAMPercentage=75` 로 컨테이너 한도에 맞춘다. 바꾸려면 `-e JAVA_TOOL_OPTIONS=...` 로 덮어쓴다.

## 전체 스택 기동 (W-34 선행)

`compose.app.yaml` 이 master 의 target 7개와 프론트(nginx)를 `compose.yaml`(DB · Redis · Kafka) 위에 올린다. `.env` 의 `JWT_SECRET_KEY` 는 필수다 (`.env.example` 을 복사).

```bash
cp .env.example .env
docker compose -f compose.yaml -f compose.app.yaml up -d --build --wait
docker compose -f compose.yaml -f compose.app.yaml --profile ai up -d --build --wait   # ai-service 포함 (PR #24 머지 후)
docker compose -f compose.yaml -f compose.app.yaml down
```

- 접속: 프론트 `http://localhost` (nginx 가 `/api/` 를 gateway 로 프록시, 포트는 `FRONTEND_PORT` 로 변경), gateway `:8000`, Eureka `:8761`.
- IDE 로 서비스를 띄우는 사람은 기존처럼 `compose.yaml` 만 쓴다. 앱 컨테이너와 IDE 서비스를 같이 띄우지 않는다 (포트 충돌).
- user · ai 의 헬스체크는 `/actuator/health` 가 401 이라 임시로 TCP 포트 검사다 (#25, PR #24 머지 후 교체).

## 서비스 코드 배치 (구조만 나눈 상태)

1차 `backend/sporty` 코드는 패키지 경계대로 서비스 디렉터리에 **복사**되어 있다(로직 수정 없음). 서비스 경계를 넘는 `import` 가 남아 있어 user · match · ai 는 아직 컴파일되지 않는다. 남은 목록은 [docs/msa-split-todo.md](docs/msa-split-todo.md) 에 있다.

```bash
cd backend
./gradlew.bat :facility-service:compileJava   # 서비스 하나만 빌드 (전체 build 는 위 서비스 때문에 실패한다)
```

자기 서비스의 경계 import 를 없애는 작업은 서비스별 작업 브랜치에서 각자 한다: `feat/user-w07-code-move`, `feat/match-w08-code-move`, `feat/facility-w09-code-move`, `feat/ai-w10-code-move`.

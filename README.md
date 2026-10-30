# Sporty

공공 체육시설 정보와 AI를 활용해 운동 매치 검색·생성·참여를 지원하는 웹 애플리케이션입니다.

> **2차 미니프로젝트 팀을 위한 기존 애플리케이션 실행 안내**  
> 아래 절차는 **MSA 전환 전 React + Spring Boot 모놀리식 버전**을 로컬에서 실행하는 방법입니다. MSA 전환 후 실행 방법은 별도로 정리합니다.  
> 현재 팀원별 실행 환경이 다를 수 있으므로, 실제로 확인된 오류는 이 문서에 계속 반영해 주세요.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | React, Create React App, Axios |
| Backend | Java 17, Spring Boot 3.5.4, Spring Data JPA, Hibernate |
| Database | MariaDB (로컬 설치) |
| Cache / 인증 관련 인프라 | Redis (Docker Compose) |
| AI | Spring AI, OpenAI API |
| 외부 데이터 | 서울시 체육시설 공공 API |

## 프로젝트 구조

```text
.
├── backend/
│   └── sporty/                    # 실제 Spring Boot / Gradle 프로젝트
│       ├── .env.example           # 백엔드 환경변수 템플릿
│       ├── .env                   # 각자 생성 (Git에 커밋하지 않음)
│       ├── gradlew.bat
│       └── src/
├── frontend/                      # React 프로젝트
│   ├── .env.example               # 프론트엔드 환경변수 템플릿
│   ├── .env                       # 각자 생성 (Git에 커밋하지 않음)
│   ├── package.json
│   └── package-lock.json
├── docs/                          # 기존 프로젝트 문서
├── compose.yaml                   # Redis 실행용 (MariaDB 미포함)
└── README.md
```

`.env` 파일은 Git으로 관리하지 않습니다. **`.env.example` 파일만 저장소에 포함**하세요.

## 1. 사전 준비

- Git
- **JDK 17**
- Node.js와 npm
- Docker Desktop (Docker Compose 사용 가능 상태)
- **로컬 MariaDB** 및 접속 가능한 DB 계정
- AI 기능에 사용할 OpenAI API Key
- 서울시 체육시설 데이터 동기화에 사용할 공공데이터 API Key

아래 명령은 **Windows Git Bash** 기준입니다. 이미 저장소를 내려받았다면 프로젝트 최상위 디렉터리에서 시작하세요.

## 2. Redis 실행

저장소 루트의 `compose.yaml`로 Redis만 실행합니다.

```bash
docker compose config
# 설정이 정상이라면
docker compose up -d redis
docker compose ps
```

- 백엔드를 로컬에서 실행하므로 Redis 주소는 `localhost:6379`입니다.
- Docker Desktop에서 이미 실행 중인 Redis가 `6379` 포트를 사용하고 있다면 기존 컨테이너를 먼저 중지하세요.
- 현재 `compose.yaml`에는 **MariaDB가 포함되어 있지 않습니다.**

Redis 컨테이너를 종료·삭제하려면 저장소 루트에서 다음을 실행합니다.

```bash
docker compose down
```

## 3. MariaDB 준비

현재는 강의에서 설치한 **로컬 MariaDB**를 그대로 사용합니다. MariaDB 서버가 실행 중인지 확인하고, 다음 DB를 생성하세요.

```sql
CREATE DATABASE IF NOT EXISTS sporty;
```

시연용 데이터 초기화 브랜치(아래 **초기 데이터** 참고)를 사용하는 경우, 기존 데이터를 보호하기 위해 별도 DB를 권장합니다.

```sql
CREATE DATABASE IF NOT EXISTS sporty_demo;
```

- 기본 MariaDB 포트는 `3306`을 예시로 사용합니다. 실제 포트가 다르다면 `DB_URL`을 조정하세요.
- 기존 `develop` 코드의 `ddl-auto`는 `update`이며, Hibernate가 엔티티를 기준으로 테이블을 생성·변경할 수 있습니다.
- **시연용 초기화 코드(PR #102)의 `ddl-auto: create-drop`은 기존 테이블과 데이터를 삭제할 수 있습니다.** 반드시 테스트 전용 DB에서만 사용하세요.

## 4. 백엔드 환경변수 설정

저장소 루트에서 `.env.example`을 복사합니다.

```bash
cp backend/sporty/.env.example backend/sporty/.env
```

`backend/sporty/.env`에서 실제 접속 정보와 API 키를 입력하세요. 기존 `application-dev.yml`이 참조하는 주요 변수는 다음과 같습니다.

```dotenv
DB_DRIVER=org.mariadb.jdbc.Driver
DB_URL=jdbc:mariadb://localhost:3306/sporty
DB_USERNAME=root
DB_PASSWORD=

OPEN_AI_KEY=
OPEN_AI_MODEL=
JWT_SECRET_KEY=

PUBLIC_DATA_API_KEY=
PUBLIC_DATA_API_URL=
```

- `DB_USERNAME`과 `DB_PASSWORD`는 **자신의 MariaDB 계정 정보**로 변경해야 합니다.
- 시연용 DB를 사용한다면 `DB_URL`을 `jdbc:mariadb://localhost:3306/sporty_demo`로 변경합니다.
- `OPEN_AI_KEY`는 **OpenAI API 키**, `PUBLIC_DATA_API_KEY`는 **서울시 체육시설 공공데이터 API 인증키**입니다. 서로 다른 키입니다.
- `OPEN_AI_MODEL`, `PUBLIC_DATA_API_URL`은 프로젝트에서 사용하는 유효한 값으로 설정해야 합니다.
- 기존 설정 파일에는 `OPENAPI_SERVICE_KEY`, `OPENAPI_CALLBACKURL`, `OPENAPI_TYPE`도 선언되어 있습니다. 확인한 체육시설 API 클라이언트는 이들 대신 `PUBLIC_DATA_API_KEY` 및 `PUBLIC_DATA_API_URL`을 사용합니다. 이전 설정을 정리하기 전까지는 다른 사용처가 있는지 추가로 확인하세요.
- API 키나 비밀번호를 `.env.example`, README, Git 커밋에 넣지 마세요.

> **주의:** `.env`가 없거나 필수 설정값이 비어 있으면 Spring Boot 시작에 실패할 수 있습니다. 위 예시의 빈 값은 실제 운영 값이 아닙니다.

## 5. 백엔드 실행

새 터미널에서 **`backend/sporty` 디렉터리**로 이동합니다.

```bash
cd backend/sporty
./gradlew.bat bootRun
```

- Windows 명령 프롬프트에서는 `gradlew.bat bootRun`, macOS/Linux에서는 `./gradlew bootRun`을 사용하세요.
- 기본 프로필: `dev`
- 백엔드 주소: **`http://localhost:8000`**
- 최초 실행 시 Gradle 의존성 다운로드가 필요할 수 있습니다.

`backend/`가 아니라 **`backend/sporty/`에서 실행**해야 합니다.

## 6. 프론트엔드 환경변수 및 실행

**프론트엔드에도 별도의 `.env` 파일이 필요합니다.** 백엔드의 `.env` 설정만으로는 React의 API 주소가 설정되지 않습니다.

저장소 루트에서:

```bash
cp frontend/.env.example frontend/.env
cd frontend
npm ci
npm start
```

`frontend/.env`에 다음 값이 들어 있는지 확인하세요.

```dotenv
REACT_APP_BACKEND_ENDPOINT=http://localhost:8000
```

- 프론트엔드 기본 주소: **`http://localhost:3000`**
- **백엔드는 8000번 포트**, 프론트엔드는 3000번 포트입니다.
- `.env.example`은 참고용 템플릿이므로 **실제 `.env` 파일을 따로 생성**해야 합니다.
- Create React App은 시작 시 환경변수를 읽습니다. **`.env`를 수정했다면 `npm start`를 종료(`Ctrl+C`)하고 재실행**하세요.
- `frontend/package-lock.json`이 있으므로 우선 `npm ci`를 사용하세요.
- `'react-scripts'은(는) ... 아닙니다` 오류가 발생하면 `frontend/`에서 `npm ci`가 성공했는지 확인하세요.

**프론트엔드에 실제 API 비밀키를 넣지 마세요.** `REACT_APP_`으로 시작하는 환경변수 값은 프론트엔드 빌드 결과물에 포함되어 사용자가 확인할 수 있습니다. 기존에 사용하던 `REACT_APP_ACCESS_TOKEN`, `REACT_APP_RT`, `REACT_APP_GPT_KEY` 등의 테스트 값은 기본 실행에 필요하지 않습니다. 로그인 시 발급받는 토큰은 별도 인증 로직에서 관리합니다.

### 로그인 요청 주소 확인

브라우저 개발자 도구(`F12`) → **Network**에서 로그인 요청을 확인하세요.

- 정상: `POST http://localhost:8000/api/auth/login`
- 잘못된 설정의 예: `POST http://localhost:3000/api/auth/login`

3000번 포트로 API 요청이 전송된다면 `frontend/.env`의 `REACT_APP_BACKEND_ENDPOINT`가 설정되었는지 확인하고 React 개발 서버를 재시작하세요.

과거 로그인 정보가 브라우저에 남아 있으면, 새 DB로 실행한 뒤에도 마이페이지가 로그인 상태로 표시될 수 있습니다. 이 경우 해당 사이트의 저장된 로그인 정보를 지우고 테스트하세요.

## 7. 초기 데이터 / 시연용 브랜치 (선택 사항)

기존 1차 프로젝트 저장소의 [PR #102 — 앱 시작 시 데이터 구성](https://github.com/livelykitten/inspire_6th_mini_1_sporty/pull/102)은 다음 코드를 추가합니다.

1. `data.sql`을 이용한 시연용 사용자·프로필·선호 종목 생성
2. 애플리케이션 시작 시 서울시 체육시설 데이터 동기화
3. 동기화된 시설을 참조하는 데모 운동 매치 생성

이 PR은 **원본 1차 프로젝트의 `feat/fetch-at-startup` 브랜치**에 있으며, 원본 `develop`에는 병합되지 않았습니다. **현재 2차 프로젝트 저장소에 해당 코드가 포함되어 있는지는 브랜치를 직접 확인**해야 합니다.

> **데이터 삭제 주의:** PR #102는 `ddl-auto: create-drop`을 사용합니다. 기존 데이터를 보관해야 하는 DB에서는 실행하지 마세요. 또한 외부 시설 API 동기화가 실패하면 시연용 매치가 준비되지 않을 수 있습니다.

## 8. 문제 해결

| 증상 | 확인 사항 |
| --- | --- |
| `go-yaml load error` | `compose.yaml` 들여쓰기·콜론 확인 후 `docker compose config` 실행 |
| Redis 포트 충돌 | 기존 Redis 컨테이너/프로세스가 `6379` 사용 중인지 확인 |
| MariaDB 연결 실패 | 로컬 DB 실행 상태, DB명, `DB_URL`, 계정·비밀번호 확인 |
| 백엔드 실행 실패 | `backend/sporty` 경로, JDK 17, `.env` 설정 및 로그 확인 |
| `react-scripts` 명령을 찾을 수 없음 | `frontend/`에서 `npm ci` 실행 |
| 로그인 API가 `localhost:3000`으로 전송됨 | `frontend/.env`의 `REACT_APP_BACKEND_ENDPOINT` 설정 후 `npm start` 재시작 |
| 로그인/마이페이지가 예상과 다르게 동작 | 브라우저에 남아 있는 로그인 토큰 및 Network 요청·응답 확인 |
| AI 기능 실패 | `OPEN_AI_KEY`, `OPEN_AI_MODEL`, API 결제·권한 및 서버 로그 확인 |
| 시설/매치 데이터 없음 | 초기 데이터 코드 포함 여부, 공공데이터 API 설정, 동기화 로그 확인 |

## Git 규칙

기존 프로젝트에서는 기능별 브랜치와 Pull Request를 사용했습니다.

```bash
git switch -c feature/<issue-number>-<feature-name>
```

커밋 메시지는 `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:` 등의 접두사를 권장합니다. **2차 프로젝트에서는 새 팀의 브랜치 및 PR 규칙을 우선합니다.**

## 프로젝트 문서

기존 요구사항 명세, API 명세, 테스트 케이스 등 프로젝트 산출물은 `docs/`에 있습니다.

---

**검증 상태:** 이 문서는 기존 프로젝트 코드와 설정에 근거한 로컬 실행 가이드입니다. 팀원 개별 PC에서 Redis, MariaDB, 백엔드, 프론트엔드 및 초기 데이터가 모두 정상 동작하는지는 각 환경에서 확인해야 합니다.

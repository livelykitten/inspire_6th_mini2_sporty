# Sporty

공공 체육시설 정보와 AI를 활용하여 운동 매칭 검색·생성·참여를 지원하는 웹 애플리케이션입니다.

> **1차 미니프로젝트 코드 인수인계 안내**  
> 기존 애플리케이션은 **React 프론트엔드 + 단일 Spring Boot 백엔드** 구조입니다. 이 저장소를 기반으로 2차 미니프로젝트에서 MSA 전환을 진행할 예정입니다. 아래는 **기존 모놀리식 버전의 로컬 실행 방법**이며, MSA 전환 후 실행 방법과는 다릅니다.
>
> 이 문서는 저장소 설정을 기준으로 작성한 안내입니다. 다른 개발자 PC에서 전 과정을 검증한 것은 아닙니다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | React, Create React App, Axios |
| Backend | Java 17, Spring Boot 3.5.4, Spring Data JPA, Hibernate |
| Database | MariaDB |
| Cache / 인증 관련 인프라 | Redis |
| AI | Spring AI, OpenAI API |
| 외부 데이터 | 서울시 체육시설 공공 API |

## 프로젝트 구조

```text
.
├── backend/
│   └── sporty/                  # 실제 Spring Boot/Gradle 프로젝트
│       ├── .env.example         # 백엔드 환경변수 예시
│       ├── gradlew.bat
│       └── src/
├── frontend/                   # React 프로젝트
│   ├── .env.example             # 프론트엔드 환경변수 예시
│   └── package.json
├── docs/                       # 프로젝트 산출물
├── compose.yaml                # 로컬 Redis 실행용
└── README.md
```

## 1. 사전 준비

- Git
- **JDK 17** (Gradle 설정 기준)
- Node.js 및 npm (`frontend/package.json`의 의존성과 호환되는 버전)
- Docker Desktop (Docker Compose 사용 가능 상태)
- MariaDB 서버 및 접속 가능한 DB 계정
- 실제 AI 기능 사용 시 OpenAI API Key
- 체육시설 데이터 동기화 시 해당 공공데이터 API Key 및 관련 설정

> 아래 셸 명령어는 **Windows Git Bash** 기준입니다. Docker Desktop 및 MariaDB는 미리 실행해 주세요.

## 2. 저장소 복제 및 기준 브랜치 선택

```bash
git clone https://github.com/livelykitten/inspire_6th_mini_1_sporty.git
cd inspire_6th_mini_1_sporty
git fetch origin
git switch develop
```

기존 팀의 개발 기준 브랜치는 `develop`입니다. 기본 브랜치 `main`과 내용이 다를 수 있습니다.

### 시연용 초기 데이터가 필요한 경우

테스트 데이터 자동 생성 코드는 **PR #102**의 `feat/fetch-at-startup` 브랜치에 있으며, `develop`에는 병합되지 않았습니다.

- PR: https://github.com/livelykitten/inspire_6th_mini_1_sporty/pull/102
- 새로 복제한 저장소라면 `git switch --track origin/feat/fetch-at-startup`으로 해당 브랜치를 선택할 수 있습니다.
- **이 브랜치의 `ddl-auto: create-drop` 설정은 실행 시 기존 테이블/데이터를 삭제하고 종료 시에도 스키마를 제거할 수 있습니다. 반드시 별도의 테스트 DB에서만 실행하세요.**
- 시작 시 서울시 API 데이터를 동기화한 다음 데모 매치를 생성하므로, 외부 API 연결과 설정이 필요합니다. 동기화 오류 시 데모 데이터가 준비되지 않을 수 있습니다.

## 3. Redis 실행 (Docker Compose)

저장소 루트의 `compose.yaml`은 Redis 컨테이너를 실행합니다.

```bash
docker compose up -d redis
docker compose ps
```

기존에 Docker Desktop에서 Redis를 직접 실행해 `6379` 포트를 사용하고 있다면 기존 컨테이너를 먼저 중지하세요.

Spring Boot를 로컬 PC에서 실행할 때 Redis 접속 주소는 `localhost:6379`입니다. 백엔드의 `application-dev.yml`에도 이 주소가 설정되어 있습니다.

Redis만 중지·삭제하려면:

```bash
docker compose down
```

## 4. MariaDB 데이터베이스 준비

MariaDB는 **현재 Compose에 포함하지 않았습니다.** 로컬 MariaDB를 실행하고, SQL 클라이언트에서 데이터베이스를 생성하세요.

일반 개발용:

```sql
CREATE DATABASE IF NOT EXISTS sporty;
```

시연용 초기 데이터 브랜치(PR #102) 사용 시에는 기존 데이터 보호를 위해 **별도 DB**를 권장합니다.

```sql
CREATE DATABASE IF NOT EXISTS sporty_demo;
```

`develop`의 JPA 설정은 `ddl-auto: update`이며, Hibernate가 엔티티를 기준으로 테이블을 생성·갱신합니다. 기존 DB에 데이터가 있는 경우에는 스키마 변경 영향을 별도로 확인하세요.

## 5. 백엔드 환경변수 설정

저장소 **루트**에서:

```bash
cp backend/sporty/.env.example backend/sporty/.env
```

`backend/sporty/.env`의 값을 자신의 환경에 맞게 입력합니다. 예시 변수명:

```dotenv
DB_DRIVER=org.mariadb.jdbc.Driver
DB_URL=jdbc:mariadb://localhost:3306/sporty
DB_USERNAME=root
DB_PASSWORD=your_db_password

OPEN_AI_KEY=your_openai_api_key
OPEN_AI_MODEL=your_model_name
JWT_SECRET_KEY=your_jwt_secret

OPENAPI_SERVICE_KEY=your_api_key
OPENAPI_CALLBACKURL=your_callback_url
OPENAPI_TYPE=your_api_type

PUBLIC_DATA_API_KEY=your_public_data_api_key
PUBLIC_DATA_API_URL=your_public_data_api_base_url
```

- PR #102 시연용으로 실행한다면 `DB_URL`의 DB명을 `sporty_demo`로 변경하세요.
- 위의 `your_...` 값은 **예시 문자열**입니다. 실제 동작하려면 유효한 값으로 교체해야 합니다.
- OpenAI 키가 없어도 어떤 기능까지 실행 가능한지는 검증되지 않았습니다. 설정값이 누락되면 애플리케이션 시작이 실패할 수 있습니다.
- 실제 비밀값이 포함된 `.env`는 **Git에 커밋하지 않습니다.**
- `application-dev.yml`은 백엔드 실행 디렉터리의 `.env`를 읽도록 구성되어 있습니다.

## 6. 백엔드 실행

새 터미널에서:

```bash
cd backend/sporty
./gradlew.bat bootRun
```

- 서버 포트: **8000** (`application-dev.yml` 기준)
- 기본 Spring 프로필: **dev**
- macOS/Linux에서는 `./gradlew bootRun`을 사용합니다.
- 최초 실행에는 Gradle 의존성 다운로드가 필요할 수 있습니다.

> `backend/`가 아니라 **`backend/sporty/`**에서 실행해야 합니다.

## 7. 프론트엔드 실행

다른 터미널에서 저장소 루트로 이동한 후:

```bash
cp frontend/.env.example frontend/.env
cd frontend
npm ci
npm start
```

프론트엔드 환경변수:

```dotenv
REACT_APP_BACKEND_ENDPOINT=http://localhost:8000
```

- `frontend/package-lock.json`이 있으므로 `npm ci`를 우선 권장합니다.
- `'react-scripts'은(는) ... 아닙니다` 오류가 발생하면 `frontend/`에서 `npm ci`가 정상 완료되었는지 확인하세요.
- 환경변수를 변경했다면 프론트엔드 개발 서버를 다시 시작해야 합니다.

## 8. 시연용 데이터와 주의사항

PR #102의 자동 초기화 버전을 사용하면 다음 동작을 의도합니다.

1. `data.sql`을 통해 데모 사용자·프로필·선호 종목을 초기화
2. 애플리케이션 시작 시 서울시 체육시설 데이터를 동기화
3. 동기화된 실제 시설을 참조하여 데모 운동 매치 생성

PR 코드에 기재된 **시연 전용** 로그인 계정은 다음과 같습니다.

| 계정 | 이메일 | 비밀번호 |
| --- | --- | --- |
| 데모 사용자 1 | `a@b.c` | `1234567890` |
| 데모 사용자 2 | `b@c.d` | `1234567890` |

> 이 계정과 비밀번호는 로컬 시연에만 사용하세요. 공개 배포 환경에서 사용하면 안 됩니다. 시설 API 동기화나 초기화가 실패하면 매치 데이터가 생성되지 않을 수 있습니다.

## 9. 문제 해결

| 증상 | 먼저 확인할 것 |
| --- | --- |
| `react-scripts` 명령을 찾을 수 없음 | `frontend/`에서 `npm ci` 실행 여부 |
| 백엔드 실행 명령이 실패함 | 현재 경로가 `backend/sporty/`인지, JDK 17 사용 중인지 |
| Redis 접속 실패 | Docker Desktop 실행 여부, `docker compose ps`, 6379 포트 충돌 |
| MariaDB 연결 실패 | DB 실행 상태, DB명, `DB_URL`, DB 사용자·비밀번호 |
| 프론트 API 요청 실패 | 백엔드 8000 포트, `REACT_APP_BACKEND_ENDPOINT`, 브라우저 개발자 도구 |
| AI 기능 동작 실패 | `OPEN_AI_KEY`, `OPEN_AI_MODEL`, 외부 API 연결 및 오류 로그 |
| 시설·매치가 보이지 않음 | PR #102 사용 여부, 공공 API 설정, 시작 시 동기화 오류 로그 |

## Git 규칙

기능 개발은 별도 브랜치에서 진행하고 Pull Request로 합칩니다.

```bash
git switch -c feature/<issue-number>-<feature-name>
```

커밋 메시지는 `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:` 등으로 분류하는 형식을 권장합니다.

## 프로젝트 문서

요구사항 명세, API 명세, 테스트 케이스 등 기존 산출물은 `docs/`에서 확인하세요.

---

**현재 상태:** 이 README는 저장소 코드와 PR #102를 확인하여 작성한 *실행 가이드 초안*입니다. 다른 개발자 PC에서의 전체 실행 및 시연 데이터 생성 성공 여부는 아직 별도로 검증해야 합니다.

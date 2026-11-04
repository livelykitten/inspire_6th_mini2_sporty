# AI Service 실행 가이드 (초안)

기능별 입력·기대 결과·오류 검증은 [테스트 케이스](TEST_CASES.md)를 참고합니다.

자연어 매치 검색·개설 초안·개인화 추천을 제공하는 서비스입니다. 포트는 `8004`, Eureka 등록 이름은 `AI-SERVICE`입니다. 자체 DB는 사용하지 않습니다.

## 현재 구현 범위

- AI-01: 자연어에서 매치 개설 폼의 초안 생성. 실제 매치는 저장하지 않습니다.
- AI-02: 자연어 검색 조건 추출 후 match-service 조회.
- AI-03: 회원 정보와 매치 후보를 조회하고, 내가 개설·참가한 매치를 제외하여 최대 3개 추천.
- Gateway가 전달한 `X-User-Id`를 인증 principal로 사용합니다. AI 서비스에서 JWT를 직접 검증하지 않습니다.
- Feign은 Gateway를 거치지 않고 Eureka의 서비스 이름으로 직접 호출합니다.

## 필요한 실행 환경

- JDK 17, 저장소의 Gradle Wrapper
- 유효한 OpenAI API 키와 사용 권한
- Config Server `8888`: 현재 OpenAI 키의 설정 매핑을 제공합니다.
- Eureka `8761`: 서비스 등록·탐색에 사용합니다.
- AI-02: 실제 match-service와 해당 서비스의 DB·설정
- AI-03: 실제 user-service·match-service와 해당 서비스의 데이터·인프라
- Gateway 경유 확인: gateway-service `8000`, 보호 API에 사용할 유효한 로그인 토큰

## 1. 로컬 OpenAI 키 설정

`backend/ai-service/.env`를 만들고 properties 형식으로 작성합니다.

```dotenv
OPEN_AI_KEY=실제_키를_로컬에서_입력
```

현재 `config-repo/ai-service.yml`은 다음 값을 제공합니다.

```yaml
spring:
  ai:
    openai:
      api-key: ${OPEN_AI_KEY:}
```

AI 서비스는 로컬 `.env`에서 `OPEN_AI_KEY`를 공급합니다. 서비스 자체 `application.yml`의 중복 API 키 선언은 제거된 상태이므로, 현재 실행 가이드는 Config Server 사용을 전제로 합니다.

> **주의: Config Server 없이는 키가 비어 있습니다.** `application.yml` 의 `spring.config.import` 에 `optional:configserver:` 가 들어 있어 Config Server(8888)가 꺼져 있어도 서비스는 에러 없이 기동하지만, `spring.ai.openai.api-key` 는 `config-repo/ai-service.yml` 에서만 내려오므로 **빈 값**이 됩니다. 이 경우 OpenAI 를 호출하는 요청(AI-01 · AI-03 추천 · AI-02 조건 추출)만 실패합니다. 키가 비었는지 의심되면 `curl http://localhost:8888/ai-service/default` 에 `spring.ai.openai.api-key` 가 보이는지 먼저 확인하세요.

### Docker Compose 로 실행할 때

Compose 로 띄울 때는 `backend/ai-service/.env` 가 아니라 **저장소 루트 `.env`** 의 `OPEN_AI_KEY` 를 씁니다 (`cp .env.example .env` 후 값 입력). Compose 가 이 값을 컨테이너 환경변수로 넣어 주면 Config Server 가 내려준 `${OPEN_AI_KEY:}` 가 그 값으로 채워집니다. 전체 스택 Compose(`compose.app.yaml`)는 W-34 통합 PR 에서 들어오며, 그 전에는 위의 로컬 실행 방식을 따릅니다.

키와 `.env`는 커밋하지 않습니다. 배포용 암호화 설정은 [Config 가이드](../../config-repo/README.md)를 따릅니다.

## 2. Postman으로 API 확인

직접 호출 기본 주소는 `http://localhost:8004`입니다. POST는 `Body → raw → JSON`을 선택합니다.

### AI-01: 개설 초안

```http
POST /api/ai/matches
X-User-Id: 7
Content-Type: application/json
```

```json
{
  "prompt": "이번 토요일 저녁 7시 강남에서 초보 풋살 10명 모집"
}
```

기대: `200`, `initialValues` 반환. 입력한 종목·지역·시간·인원 등을 확인합니다. 실제 매치 저장은 match-service의 별도 생성 API 역할입니다.

### AI-02: 자연어 검색

```http
POST /api/ai/matches/search
Content-Type: application/json
```

```json
{
  "prompt": "강남에서 초보 풋살 찾아줘"
}
```

기대: `200`, `conditions`, `matches`, `criteria` 반환. 검색 조건의 `FUTSAL`, `강남구`, `BEGINNER`와 실제 match-service의 조회 결과를 확인합니다. 결과가 없으면 `matches`는 빈 배열일 수 있습니다.

### AI-03: 개인화 추천

```http
GET /api/ai/matches/recommendations
X-User-Id: 실제_존재하는_회원_ID
```

Body는 없습니다. 기대: `200`, 최대 3개의 매치 배열. 후보가 없으면 `[]`이며 OpenAI 추천을 호출하지 않습니다. 내 매치 제외와 성별·지역·선호 종목 조건을 확인합니다. 이전 Mock의 고정 ID 103·104·105를 실제 연동의 기대값으로 사용하지 않습니다.

직접 헤더를 붙이는 방식은 내부 개발 테스트용입니다. 브라우저의 실제 요청은 Gateway를 거쳐야 합니다.

## Gateway 연동

Config Server를 먼저 실행하고 Gateway를 기동합니다. 현재 Gateway 라우트·CORS·JWT 설정은 Config 저장소에서 제공됩니다.

보호 API인 AI-01·AI-03은 `http://localhost:8000`으로 요청하며 다음 헤더를 사용합니다.

```http
Authorization: Bearer <user-service에서_발급한_access_token>
```

프론트가 `X-User-Id`를 직접 지정하지 않습니다. Gateway가 외부 헤더를 제거하고 JWT 검증 결과로 다시 설정합니다. AI-02 검색은 공개 API입니다.

CORS는 Gateway가 담당합니다. `Origin: http://localhost:3000`을 포함한 8000 경유 응답에서 `Access-Control-Allow-Origin`이 한 번만 있는지 확인합니다.

## Feign 계약과 타임아웃

| 구분 | 대상 | 요청 | 응답 |
|---|---|---|---|
| IN-03 | match-service | GET `/api/matches` + 검색 쿼리 | `List<MatchResponseDto>` |
| IN-04 | match-service | GET `/api/matches/me` + `X-User-Id` | `List<MyMatchResponseDto>` |
| IN-05 | user-service | GET `/api/users/me` + `X-User-Id` | `UserInfoResponseDto` |

- Feign: 연결 2초·읽기 3초
- 동기 OpenAI HTTP 호출: 10초 (`ai.openai.timeout`)
- Spring AI 자동 재시도: 없음 (`max-attempts: 1`)

## 오류 응답

| 상황 | 상태·코드 |
|---|---|
| 공백 prompt·500자 초과·AI 검색/초안 조건 부족 | 400, `code: null`, `message` 안내 |
| 미지원 종목 | 400, 지원 종목 안내 |
| 추천·초안 요청의 인증 정보 누락/잘못된 사용자 헤더 | 401 |
| 회원 조회 API의 404 | 404, `USER_NOT_FOUND` |
| Feign 연결 실패·타임아웃·상대 서비스 503 | 503, `AI_DEPENDENCY_UNAVAILABLE` |
| OpenAI 추천 호출·결과 처리 실패 | 500, `code: null`, 추천 실패 안내 |

503의 메시지는 `잠시 후 다시 시도해주세요.`입니다. 다른 Feign 오류는 일반 500으로 처리합니다.

## 남은 작업 / GitHub 이슈 초안

다음 항목은 실제 검증 결과를 붙여 완료 처리합니다. 이 문서 작성만으로 GitHub 이슈가 생성되지는 않습니다.

- [ ] **[W-20] 실제 회원·매치 서비스 통합 검증**: IN-03~05의 쿼리·날짜·enum·사용자 헤더·오류 응답 확인. 내 매치 목록이 개설·참가를 모두 포함하는지 확인.
- [ ] **OpenAI 배포 키 중앙 암호화 전환**: Config 담당과 `ENCRYPT_KEY` 준비 후 `ai-service-dev.yml`에 `{cipher}` 값 반영, 실제 배포 프로필의 복호화·AI 호출 확인.
# 기여 가이드 (초안)

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

1. 작업 1건 = 브랜치 1개: `feat/{서비스}-{작업}` (예: `feat/user-w07-code-move`)
2. PR 제목은 `[W-xx] 제목`, 본문에 AI 사용 여부를 한 줄 적는다.
3. 승인 1명 이상 후 머지, 증빙 이미지를 남기고 시트 WBS 상태를 "완료"로 바꾼다.
4. 명세 변경은 상대 담당 + 민경완 합의 → 팀 시트 수정 → 디코 #명세-변경 순서로 한다.

## 규칙

- 서비스는 자기 DB 만 읽고 쓴다. 다른 서비스 데이터는 조회 = Feign, 복제 · 상태 전파 = Kafka 로만 가져온다.
- 서비스 간 공통 모듈은 두지 않는다. 응답 DTO 는 호출하는 서비스에 복사한다.
- 비밀값(`.env`)은 커밋하지 않는다.

## 서비스 이미지 빌드 (W-23)

서비스 7개가 `backend/Dockerfile` 하나를 공용으로 쓴다. 빌드 컨텍스트는 `backend/` 이고 서비스는 build arg 로 고른다 (멀티스테이지: JDK 17 로 `bootJar` → JRE 17 비루트 실행).

```bash
docker build -f backend/Dockerfile --build-arg SERVICE=gateway-service --build-arg PORT=8000   -t sporty/gateway-service:local backend
```

| SERVICE | PORT |
|---|---|
| gateway-service | 8000 |
| discovery-service | 8761 |
| config-service | 8888 |
| user-service | 8001 |
| match-service | 8002 |
| facility-service | 8003 |
| ai-service | 8004 |

- 지금은 경계 import 가 남은 user · match · ai 는 `bootJar` 가 컴파일 단계에서 실패해 이미지가 만들어지지 않는다. 서비스 이동(W-07, W-08, W-10)이 끝나면 같은 명령으로 빌드된다.
- 이미지 안에는 `.env` 를 넣지 않는다 (`backend/.dockerignore`). 설정은 환경변수로 넘긴다.
- JVM 메모리는 `JAVA_TOOL_OPTIONS` 의 `MaxRAMPercentage=75` 로 컨테이너 한도에 맞춘다. 바꾸려면 `-e JAVA_TOOL_OPTIONS=...` 로 덮어쓴다.

## 서비스 코드 배치 (구조만 나눈 상태)

1차 `backend/sporty` 코드는 패키지 경계대로 서비스 디렉터리에 **복사**되어 있다(로직 수정 없음). 서비스 경계를 넘는 `import` 가 남아 있어 user · match · ai 는 아직 컴파일되지 않는다. 남은 목록은 [docs/msa-split-todo.md](docs/msa-split-todo.md) 에 있다.

```bash
cd backend
./gradlew.bat :facility-service:compileJava   # 서비스 하나만 빌드 (전체 build 는 위 서비스 때문에 실패한다)
```

자기 서비스의 경계 import 를 없애는 작업은 이 배치 브랜치에서 분기해 각자 한다. 예: `feat/user-w07-code-move`, `feat/match-w08-code-move`, `feat/facility-w09-code-move`, `feat/ai-w10-code-move`.

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

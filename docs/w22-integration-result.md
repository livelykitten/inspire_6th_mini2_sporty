# W-22 회원 탈퇴 Saga 통합 검증

## 검증 기준

- 기준 커밋: `8408c70` (`develop`, user-service PR #42 및 match-service PR #53 병합 후)
- 검증일: 2026-11-03
- 실제 구성: Gateway 8000 → user-service 8001 / match-service 8002, Eureka 8761, Config 8888
- 저장소: Docker MariaDB 11의 user_db / match_db, Redis 7, Kafka 3.9.1
- 프런트 브라우저 조작은 수행하지 않았다. 사용자 API는 게이트웨이에 Bearer 토큰을 보내 호출했다.

## 실행 방법

PowerShell 7에서 저장소 루트로 이동한 뒤 실행한다.

```powershell
pwsh -File scripts/integration/w22-integration-check.ps1
```

선행 조건:

1. `docker compose up -d --wait`로 인프라를 실행한다.
2. Eureka, Config, user-service, match-service, Gateway를 실행한다. Gateway보다 Config를 먼저 실행한다.
3. Gateway와 user-service의 JWT 키가 같아야 한다. 이번 로컬 실행에서는 Config의 복호화 키가 설정되지 않아 Gateway의 jwt.secret이 누락됐다. 기존 user-service의 JWT 키를 Gateway 프로세스의 `JWT_SECRET` 환경변수로만 전달했다. 비밀값과 개인 설정은 커밋하지 않았다.
4. DB 사용자에게 테스트 시설 스냅샷 INSERT와 검증용 SELECT 권한이 있어야 한다. 스크립트는 Compose 컨테이너에 설정된 DB 계정을 사용한다.

매 실행마다 고유한 회원 3명과 테스트 시설 스냅샷, 매치 2개를 생성한다. 기존 회원/매치를 초기화하지 않는다. 테스트 데이터는 확인을 위해 남기며, 테스트 회원의 본인 소유 매치와 참가 기록은 실제 탈퇴 처리로 삭제된다. 토큰과 비밀번호는 출력하지 않는다.

## 통합 검증 결과

성공 실행 ID: `9485ca02a857`

| 항목 | 결과 |
| --- | --- |
| 게이트웨이 회원가입 및 로그인 | 각 201 / 200 |
| 탈퇴 회원의 매치 생성 및 다른 회원 매치 참가 | 201, 정원 2명 충족 후 CLOSED |
| 게이트웨이 탈퇴 접수 | 202 |
| user_outbox → Kafka EV-02 → match-service 처리 → match_outbox → Kafka EV-03 → user-service | 실제 서비스 연결 성공 |
| 탈퇴 확정 | WITHDRAWN, sagaId null |
| Redis Refresh Token | 존재 1 → 0 |
| 탈퇴 회원 소유 매치 | 상세 API 404 |
| 다른 회원 소유 매치 | 보존, 참가 인원 2 → 1, CLOSED → RECRUITING |
| 탈퇴 회원의 match_participant | 0건 |
| match_user_guard | is_blocked = 1 |
| processed_event | 해당 EV-02 eventId 1건 |
| match_outbox | 해당 회원 EV-03 SENT 1건 |
| EV-03 데이터 | deletedMatchIds / leftMatchIds에 해당 매치 ID 포함, EV-02와 sagaId 일치 |
| 탈퇴 완료 후 내 정보 | 401 USER_ALREADY_WITHDRAWN |
| 탈퇴 완료 후 로그인 / Refresh Token 재발급 | 각각 401 |
| MariaDB 동시 탈퇴 요청 | 202 1건 + 409 WITHDRAWAL_ALREADY_PENDING 1건, EV-02 Outbox 1건 |

성공 실행의 테스트 데이터: 탈퇴 회원 14, 잔존 매치 소유 회원 15, 동시 요청 회원 16, 삭제된 매치 22, 보존된 매치 23, 시설 스냅샷 9261103210939.

## 통합 과정에서 발견한 오류와 수정

기존 H2 동시 요청 테스트는 통과했지만 실제 MariaDB 동시 요청은 두 번 연속 `202 + 500`으로 실패했다. 소비자를 실행하지 않은 별도 진단 인스턴스에서도 재현돼 Kafka 처리와 독립적인 문제임을 확인했다.

서버 원인:

```text
SQL Error: 1020, SQLState: HY000
Record has changed since last read in table 'user'; try restarting transaction
```

일반 조회로 회원을 읽은 뒤 두 트랜잭션이 같은 회원의 조건부 UPDATE를 실행할 때 발생했다. 탈퇴 접수의 최초 회원 조회에 기존 `findCurrentByIdForUpdate()`를 사용해 동일 회원 요청을 직렬화했다. 비밀번호·상태 검사, 조건부 UPDATE, Outbox 저장은 기존과 동일한 한 트랜잭션 안에서 수행된다. HTTP/API/이벤트 명세는 변경하지 않았다.

잠금은 비밀번호 검사 중에도 유지되므로 동일 회원의 탈퇴 요청은 대기할 수 있다. 다른 회원 행에는 이 잠금을 적용하지 않는다.

## 자동 테스트

```powershell
cd backend
./gradlew.bat :user-service:test :match-service:test
```

- user-service: 55건, 실패 0건
- match-service: 157건, 실패 0건
- 수정 후 user-service 테스트를 재실행했고 match-service는 동일 코드의 직전 성공 결과가 UP-TO-DATE로 재사용됐다.

## 범위

- 이 문서는 W-22 정상 경로 및 실제 MariaDB 동시 요청 검증을 다룬다.
- 실패한 시험 실행에서도 생성된 테스트 회원/시설은 남아 있다. 스크립트는 자동으로 기존 데이터를 삭제하지 않는다.
- EV-04 실패 보상, ACTIVE 복원, 실패 주입은 W-32 별도 작업이다.
- 프런트 브라우저 화면, 중복 이벤트 재전송, Redis 장애 복구 시나리오는 이번 통합 스크립트의 검증 범위가 아니다.

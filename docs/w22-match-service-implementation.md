# W-22 match-service 구현 및 W-32 인계

기준: [Issue #41](https://github.com/livelykitten/inspire_6th_mini2_sporty/issues/41),
[W-02 팀 공유 명세](https://docs.google.com/spreadsheets/d/1A8f3t_F_qhmwYJm5k1pHbWqDvcinRsun/edit),
`docs/withdrawal-saga` 브랜치의 `docs/withdrawal-saga.md` 검토본.
계약은 W-02의 EV-02~04, match_db 테이블 정의를 우선했다.

현재 브랜치는 `feat/match-w22-saga`다. W-17 PR #40 머지 후 `origin/develop`의
`0d964ff`까지 fast-forward했고, 기존 W-22 작업을 복원했다.
W-17의 Feign 설정과 예외 처리기를 그대로 유지하며, 임시 MemberLookup 삭제도 반영했다.
W-17 단위 테스트의 생성자 의존성과 참가 존재 조회 stub만 W-22 경계에 맞췄다.
브랜치 전환, 새 커밋 생성, 푸시는 하지 않았다. user-service 코드는 변경하지 않았다.

## 정상 경로

1. `WithdrawalRequestedListener`가 EV-02 JSON을 읽고 `WithdrawalService`를 호출한다.
2. 이벤트 UUID, 필수 필드, 양수 userId를 검사한다. processed_event가 있으면 재정리하지 않는다.
3. DB 트랜잭션 밖에서 IN-01을 호출하고 응답 userId, WITHDRAWAL_PENDING 상태, sagaId를 검증한다.
   과거 Saga는 무시하며, 조회 실패나 잘못된 응답은 예외를 전파한다.
4. 별도 빈인 `WithdrawalTransactionService.complete()`의 실제 DB 트랜잭션을 시작한다.
5. `match_user_guard`를 원자적으로 INSERT/기존 행 유지한 뒤 FOR UPDATE로 잠근다.
   잠금 뒤 processed_event를 다시 FOR UPDATE로 읽는다. REPEATABLE READ의 과거 스냅샷에 의존하지 않는다.
6. 기존 `MatchService.removeMatchesForWithdrawal()`을 재사용한다. 매치를 ID 순으로 잠그고,
   OWNER이면 모든 참가 기록과 매치를 삭제한다. 일반 참가이면 본인 참가만 삭제하고 기존 모집 재개 규칙을 적용한다.
   match 테이블에는 별도 owner_id가 없으며 OWNER 참가 행이 소유 관계의 기준이다.
7. Guard=true, EV-03 Outbox(PENDING), processed_event INSERT를 같은 트랜잭션에서 기록한다.
   EV-03은 새 UUID, 서울 시간의 초 단위 문자열, 삭제/탈퇴한 실제 매치 ID 목록을 담는다.
8. 트랜잭션 프록시가 커밋을 마친 후에만 리스너가 acknowledge한다.
9. `MatchOutboxPublisher`가 1초마다 최대 100건을 ID 순서로 처리한다.
   `MatchOutboxSender`는 한 행을 잠그고 Kafka ack를 최대 5초 기다린다.
   성공하면 SENT/sent_at, 실패하면 PENDING 유지/attempt_count 증가다.
   실패한 첫 행에서 해당 배치를 중단하여 발행 순서를 유지한다.

동일 eventId의 순차 중복은 첫 processed_event 조회에서 종료한다.
동시 중복은 Guard 잠금으로 직렬화한 뒤 두 번째 processed_event 조회로 차단한다.
event_id PK는 마지막 안전장치이며 INSERT 오류를 무조건 성공으로 삼키지 않는다.
이미 Guard가 차단된 회원도 결과 Outbox를 새로 생성하지 않는다.

## EM-01·06의 트랜잭션 경계

`MatchService.createMatch()`와 `joinMatch()`는 NOT_SUPPORTED로 호출자의 트랜잭션을 중단하고
IN-01 검증을 수행한 뒤 TransactionTemplate으로 쓰기 트랜잭션에 진입한다.
EM-06은 없는 매치의 기존 404 우선순위를 위해 잠금 없는 존재 조회를 먼저 하며,
쓰기 트랜잭션에서 실제 매치를 다시 잠금 조회한다.

쓰기 트랜잭션은 항상 회원 Guard → 매치 순서로 잠근다. Guard=true이면 USER_WITHDRAWN이다.
ACTIVE 조회 직후 탈퇴 정리가 먼저 완료됐더라도 뒤늦은 생성/참가가 차단된다.
생성/참가가 먼저 커밋됐다면 탈퇴 정리가 그 기록까지 정리한다.
새로 생성한 Guard의 기존 차단 값을 upsert로 초기화하지 않는다.

기존 코드처럼 외부 호출 전체를 포함한 트랜잭션에 의존하던 테스트는 새 경계에 맞췄다.
참가 롤백 테스트는 실제 쓰기 트랜잭션의 beforeCommit에서 SQL 반영을 확인한 뒤 실패시켜
참가 기록과 CLOSED 상태가 모두 롤백되는 기존 검증을 유지한다.

## W-32 스켈레톤

- `WithdrawalFailedEvent`: EV-04 DTO만 준비했다. 현재 정상 경로에서는 발행하지 않는다.
- `WithdrawalFailureService.recordFailure()`: REQUIRES_NEW 경계를 준비했으며 아직 예외를 던진다.
  TODO: 롤백 확정/커밋 불명확 구분, Guard 잠금, 처리 결과 재확인, processed_event+EV-04 원자적 저장.
- `WithdrawalCleanupHook`: 기본 비활성화. `saga.fail-cleanup=true`이면 정리 후 트랜잭션 내부에서 예외를 던진다.
  이 설정을 켜도 아직 보상 복원은 수행하지 않는다.
- EV-02 전용 Kafka factory: auto commit=false, MANUAL_IMMEDIATE, 동기 offset commit,
  1초 간격 3회 재시도 및 전용 Recoverer 연결. 기존 시설 이벤트 설정은 건드리지 않는다.
- `WithdrawalExhaustedRecoverer`: TODO로 남긴 복구/DLT 처리를 성공으로 반환하지 않고 예외를 던진다.
  ackAfterHandle=false, commitRecovered=false이므로 미구현 경로가 완료 처리되지 않게 했다.
  실제 브로커에서 offset/재시도 소진 동작은 반드시 추가 검증해야 한다.

내일은 기존 성공 커밋 여부 재확인, 안전한 EV-04 기록, `.DLT` 전송 성공 확인,
그 이후의 offset 완료, user-service ACTIVE 복원 연동을 구현해야 한다.
실패 레코드는 오늘 구현 상태에서 파티션 진행을 막을 수 있다. W-32 전에는 자동 보상을 기대하면 안 된다.

## 설정·스키마·테스트

- `saga.fail-cleanup`: 기본 false (`SAGA_FAIL_CLEANUP`).
- `saga.outbox.enabled`: 기본 true (`MATCH_OUTBOX_ENABLED`).
- `saga.outbox.publish-delay-ms`: 기본 1000.
- 테스트용 H2는 MariaDB 호환 모드에서 Guard upsert를 실행한다.
- 실제 MariaDB 검증 환경은 `src/main/resources/db/withdrawal-saga.sql`로 신규 세 테이블을 생성한 뒤
  기존처럼 ddl-auto=validate를 유지한다. 기존 테이블을 변경하거나 검증을 생략하지 않는다.
- 일반 애플리케이션의 ddl-auto=update도 새 JPA 엔티티를 바탕으로 테이블을 생성한다.

```powershell
cd backend
.\gradlew.bat :match-service:test --tests '*Withdrawal*Test'
.\gradlew.bat :match-service:test :match-service:bootJar
```

자동 테스트는 정상 정리/EV-03 payload, 매치 없는 회원, 순차·동시 중복,
과거 Saga 무시, 조회 실패 전파, 삭제 후 실패/Outbox INSERT 후 실패 롤백,
커밋 후 ack, 실패 시 ack 금지, ACTIVE 사전 조회와 탈퇴 정리의 경합,
Outbox 실패 후 동일 payload 재시도, 미구현 보상/Recoverer의 예외 전파를 검증한다.
Saga 테스트는 H2와 KafkaTemplate mock을 사용하며 실제 Kafka 송수신을 검증한 것은 아니다.
기존 MatchIntegrationTest는 로컬 dev 설정의 실제 MariaDB를 사용한다.

최종 실행 결과: `:match-service:test :match-service:bootJar` 성공.
W-17 머지 반영 후 전체 155건 통과, 실패 0건, 건너뜀 0건.
신규 테스트는 WithdrawalSagaIntegrationTest 15건, WithdrawalListenerTest 5건으로 총 20건이다.
`git diff --check`도 통과했다. 실제 Kafka 통합 실행 및 user-service 최종 상태 전이는 수행하지 않았다.

## 실제 Kafka 및 직접 리뷰 항목

1. EV-02를 실제 브로커에 넣고 user-service IN-01 → DB 정리 → EV-03 → WITHDRAWN 연동을 확인한다.
   메시지 key=userId, JSON 문자열, occurredAt 포맷, 토픽·그룹·serializer 설정도 확인한다.
2. DB 커밋 직후 ack 전 프로세스 종료 후 재수신, 순차/동시 중복, 다중 인스턴스 Guard 경합을 검증한다.
   신규 Saga 동시성 자동 테스트는 H2에서 검증했으므로 MariaDB REPEATABLE READ에서도 재현해야 한다.
3. Kafka 중단 후 PENDING/attempt_count, 회복 후 SENT와 동일 eventId/payload 재전송을 확인한다.
   Kafka 성공 후 DB의 SENT 커밋 실패 시 중복 발행은 가능하다. 수신자의 멱등성이 필요하다.
4. 발행기는 전송 대기 동안 Outbox 행 잠금을 보유한다. 다중 발행기 경합과 DB 락 대기,
   첫 실패 행이 뒤의 발행을 지연시키는 정책을 리뷰한다.
5. W-32 이전에는 복구기가 의도적으로 실패한다. 재시도 소진·offset 유지와 파티션 정체를 확인하고
   운영자가 해당 레코드를 조사할 수 있어야 한다. DLT 발행/보상은 아직 구현되지 않았다.
6. W-17 머지와 W-22 변경의 충돌은 해결했다. EM-01·06을 호출자의 기존 쓰기 트랜잭션에
   포함시키던 호출부가 있다면 새 경계를 검토한다.

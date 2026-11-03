# 회원 탈퇴 Saga 구현 설계 (Sporty MSA)

> **문서 상태:** 2026-11-02 명세 기반 구현 설계 검토본 — 담당자 간 최종 확인 필요

> **작성 기준:** 2026-11-02 공유 명세의 USR-04~06, IN-01, EM-01·06, EV-02~04, `user_outbox`·`match_outbox`·`processed_event`·`match_user_guard` 및 W-22·W-32

> **범위:** `user-service` ↔ Kafka ↔ `match-service`의 회원 탈퇴 처리. 프론트엔드의 완료 대기·자동 확인 기능은 포함하지 않는다.

> **명세 우선:** API 응답·이벤트 필드·DB 컬럼·오류 코드의 정식 정의는 팀 공유 시트가 기준이다. 아래 구현 절차와 예외 처리 권고안은 담당자 합의 후 확정한다.

## 1. 목표와 불변 조건

`DELETE /api/users/me`(USR-04) 요청을 접수하면 회원을 **`WITHDRAWAL_PENDING`**으로 전환하고, Kafka 이벤트를 통해 매치 정리를 비동기로 수행한다. 정리가 성공하면 `WITHDRAWN`, 최종 실패하면 `ACTIVE`로 전환한다. HTTP **`202 Accepted`는 요청 접수**를 뜻하며 탈퇴 완료를 보장하지 않는다. 응답 본문은 없다.

반드시 지킬 조건:

1. 회원 상태를 `WITHDRAWAL_PENDING`으로 바꾸는 작업과 EV-02 Outbox 기록은 **같은 user DB 트랜잭션**에서 원자적으로 완료한다.
2. 매치 정리, `match_user_guard.is_blocked=true`, `processed_event` 기록, EV-03 Outbox 기록은 **같은 match DB 트랜잭션**에서 완료한다. 하나라도 실패하면 모두 롤백한다.
3. 매치 생성·참가와 회원 탈퇴 정리는 **동일 회원의 Guard 행을 먼저 잠근다.** Guard 값과 DB 행 잠금은 서로 다른 개념이다.
4. Kafka 재전송·재수신은 발생할 수 있다. 동일 이벤트 재발행 시 **`eventId`와 payload를 변경하지 않고**, 소비자는 중복 처리를 방지한다.
5. EV-03·04는 `user.status=WITHDRAWAL_PENDING` **및** `withdrawal_saga_id=sagaId`가 일치하는 경우에만 상태를 바꾼다. 과거 Saga의 지연 메시지는 무시한다.
6. Kafka 발행 성공 여부와 DB 커밋을 하나의 분산 트랜잭션으로 묶지 않는다. 각 서비스의 **Transactional Outbox**를 이용한다.

## 2. 참여 서비스·이벤트·상태

| 구분 | 책임 |
| --- | --- |
| `user-service` (송석주) | USR-04 접수, 회원 상태·Saga ID 저장, `user_outbox` 발행, EV-03·04 수신·상태 전이, 인증 정책 |
| `match-service` (Saga: 민경완 / 기존 매치 API: 김예림, 세부 분담 협의) | EV-02 수신, Guard 잠금, 회원 관련 매치·참가 정리, `processed_event` 기록, `match_outbox` 발행. 기존 EM-01·06에 Guard 연계 필요 |
| Kafka | EV-02~04 전달. 토픽별 `userId`를 message key로 사용 |
| 프론트엔드 | USR-04의 202를 **탈퇴 요청 접수**로 취급. 완료 확인 폴링은 이번 범위에서 제외 |

| ID | 토픽 | 발행 → 수신 | 필드(공통 `eventId`, `occurredAt`) |
| --- | --- | --- | --- |
| EV-02 | `user.withdrawal.requested` | user → match | `sagaId`, `userId` |
| EV-03 | `match.withdrawal.completed` | match → user | `sagaId`, `userId`, `deletedMatchIds`, `leftMatchIds` |
| EV-04 | `match.withdrawal.failed` | match → user | `sagaId`, `userId`, `reason` |

- `userId`: 대상 회원 ID. Kafka key는 **문자열로 변환한 `userId`**.

- `sagaId`: **회원 탈퇴 시도 1회**의 UUID. EV-02 및 그에 대한 EV-03 또는 EV-04에서 동일.

- `eventId`: **개별 이벤트**의 UUID. EV-02·03·04에는 서로 다른 값이지만, 동일 Outbox 행 재발행 시 변경하지 않는다.

- `occurredAt`: 명세상의 Asia/Seoul 기준 초 단위 ISO-8601 문자열.

회원 상태 전이:
```text
ACTIVE
  │ USR-04 비밀번호 확인 + 상태 변경 + user_outbox 커밋
  ▼
WITHDRAWAL_PENDING ── EV-03 수신(일치하는 sagaId) ──▶ WITHDRAWN
  │
  └────────────────── EV-04 수신(일치하는 sagaId) ──▶ ACTIVE
```
각 전이에서 `withdrawal_saga_id`는 시작 시 새 UUID로 설정하고, EV-03·04 적용 성공 시 `NULL`로 지운다. 이미 `WITHDRAWAL_PENDING`인 회원에 대해 새 Saga를 중복 시작하지 않는다.

## 3. 정상 경로 (W-22)

### 3.1 user-service: USR-04 → EV-02

1. 로그인 사용자를 식별하고 요청 본문의 `password`를 검증한다. 불일치 시 기존 `400 PASSWORD_MISMATCH` 규칙을 따른다.
2. 새로운 `sagaId`, EV-02용 `eventId`를 생성한다.
3. **user DB 단일 트랜잭션**에서 다음을 처리한다.

   - `status=ACTIVE`인 사용자만 `WITHDRAWAL_PENDING`으로 바꾸고 `withdrawal_saga_id=sagaId`를 저장한다. `UPDATE` 반영 행 수는 반드시 **1건**이어야 한다.

   - 동일 트랜잭션에서 `user_outbox`에 EV-02 (`PENDING`)를 INSERT한다.

4. DB 커밋 **후** HTTP `202 Accepted`를 반환한다. 요청 처리 중 Kafka 발행 성공을 기다릴 필요는 없다.
5. 별도 Outbox 발행기가 `user_outbox`의 PENDING 이벤트를 Kafka로 전송한다.

조건부 UPDATE 예시(개념용):
```sql
UPDATE user
   SET status = 'WITHDRAWAL_PENDING',
       withdrawal_saga_id = :sagaId
 WHERE id = :userId
   AND status = 'ACTIVE';
-- 영향 행 수가 1일 때만 같은 트랜잭션에서 user_outbox INSERT
```
`202`를 반환했더라도 이벤트 발행이나 매치 정리는 아직 진행 중일 수 있다. Outbox 발행은 HTTP 요청 스레드가 아니라 별도 스케줄러/워커가 수행한다.

### 3.2 match-service: EV-02 → EV-03

이벤트 중복 여부와 IN-01을 통한 현재 Saga 상태를 먼저 확인한 후, Guard 잠금부터 매치 정리 및 결과 Outbox 기록까지 하나의 match DB 트랜잭션에서 수행한다.

1. `processed_event`에서 수신 `eventId`가 이미 처리됐는지 빠르게 확인한다. 기록이 있으면 매치 재정리나 새 결과 이벤트를 만들지 않는다. **이 첫 조회만으로 중복 방지가 완성되는 것은 아니다.**
2. IN-01로 현재 회원 상태와 Saga ID를 검증한다. GET /internal/users/{userId}/status를 호출하여 status=WITHDRAWAL_PENDING이고 응답의 sagaId가 EV-02의 sagaId와 일치하는지 확인한다. 일치하지 않는 과거 이벤트는 매치 정리 없이 무시한다. 회원 서비스 조회에 실패하면 정상 처리된 것으로 간주하지 않고 재시도 또는 조사 대상으로 처리한다. 이 외부 호출은 Guard 잠금 및 match DB 트랜잭션 밖에서 수행한다.
2. 대상 `userId`의 `match_user_guard` 행이 없으면 **원자적으로 생성**하고, 해당 행을 `SELECT ... FOR UPDATE`로 잠근다. 기존 행의 `is_blocked` 값을 초기화해서는 안 된다.
3. **Guard 잠금 획득 후 같은 트랜잭션에서 `processed_event`를 `SELECT ... FOR UPDATE` 등의 최신 커밋이 보이는 잠금 조회로 다시 확인한다.** 다른 Consumer가 잠금을 먼저 획득해 처리를 마쳤을 수 있기 때문이다. MariaDB InnoDB의 기본 `REPEATABLE READ`에서는 일반 SELECT를 반복하면 앞서 생성된 스냅샷을 다시 읽을 수 있어 단순 재조회로는 부족하다. 이미 기록이 있으면 중단한다. 이후 `processed_event` INSERT의 PK 충돌도 중복 처리로 분류하되, 다른 DB 오류를 중복으로 간주하지 않는다.
4. `is_blocked` 상태와 현재 정리 대상 매치·참가를 확인한다. 실제 정리 대상은 매치 생성자 관계와 참가자 관계 양쪽이다.
5. 회원이 생성한 매치와 그 연관 참가 기록을 정리하고, 다른 매치에서의 회원 참가도 정리한다. 삭제 순서는 실제 FK 및 연관관계에 맞게 정한다.
6. 같은 트랜잭션에서 `match_user_guard.is_blocked=true`로 설정한다.
7. 같은 트랜잭션에서 `processed_event(eventId, topic, processed_at)`를 INSERT하고, `match_outbox`에 EV-03 (`PENDING`)을 INSERT한다. EV-03에는 정리된 ID 목록을 담는다.
8. **match DB 커밋 이후** 해당 Kafka 레코드의 소비를 완료(오프셋 커밋/ack)한다. Outbox의 Kafka 전송 완료까지 기다리지는 않는다.
9. 별도 Outbox 발행기가 EV-03을 Kafka로 보낸다.

**중요:** Kafka 오프셋을 DB 커밋 전에 확정하면 실패한 DB 작업이 재처리되지 않을 수 있다. 반대로 DB 커밋 직후 ack 전에 프로세스가 죽더라도 `processed_event`로 중복 수신을 안전하게 무시할 수 있어야 한다.

### 3.3 user-service: EV-03 → WITHDRAWN

EV-03 수신 시 아래의 **조건부 UPDATE**를 수행한다.

```sql
UPDATE user
   SET status = 'WITHDRAWN',
       withdrawal_saga_id = NULL
 WHERE id = :userId
   AND status = 'WITHDRAWAL_PENDING'
   AND withdrawal_saga_id = :sagaId;
```
- 반영 행 수 **1**: 이 Saga의 정상 완료. 이후 Refresh Token 무효화/삭제 처리.

- 반영 행 수 **0**: 이미 완료됐거나 이전 Saga의 지연 이벤트일 수 있으므로 **상태를 덮어쓰지 않는다.** 이미 WITHDRAWN으로 확정된 경우 Redis 토큰 삭제가 누락되었는지도 재확인할 수 있도록 한다.

- Refresh Token이 Redis에 있다면 MariaDB 상태 변경과 Redis 삭제는 **같은 DB 트랜잭션이 아니다**. Redis 삭제 실패를 대비해 별도 재시도/운영상 확인 경로가 필요하다. EV-03을 다시 받았을 때 조건부 UPDATE가 0건이었다는 이유로 누락된 토큰 정리를 영원히 건너뛰어서는 안 된다. **토큰 재발급 API는 Redis 키 유무와 무관하게 현재 회원 상태가 `WITHDRAWN`이면 반드시 거부**해야 한다.

## 4. 실패·보상 경로 (W-32)

### 4.1 match-service 매치 정리 실패

EV-02 처리 중 정리·Guard 갱신·Outbox 기록 중 하나라도 실패하면 **해당 match DB 트랜잭션 전체를 롤백**한다. 따라서 부분 삭제나 `is_blocked=true`가 남지 않는다.

- 일시적 오류는 **최초 처리 1회 + 1초 간격 재시도 3회**(공유 Kafka 명세) 범위에서 재시도한다.

- 재시도 후 **정리 실패가 최종 확정**되고 기존 성공 커밋 여부가 명확히 부정된 경우, **별도 match DB 트랜잭션**에서 `processed_event(EV-02.eventId)`와 `match_outbox(EV-04, PENDING)`를 함께 INSERT한다.

- 이 실패 결과 기록이 커밋된 **후에만** EV-02 소비를 완료한다. Outbox 발행기가 EV-04를 Kafka로 전송한다.

- EV-04를 받은 user-service는 `WITHDRAWAL_PENDING`과 일치하는 `sagaId`를 조건으로 회원을 `ACTIVE`로 복원하고 Saga ID를 지운다. 기존 Refresh Token을 보존한다.
```sql
UPDATE user
   SET status = 'ACTIVE',
       withdrawal_saga_id = NULL
 WHERE id = :userId
   AND status = 'WITHDRAWAL_PENDING'
   AND withdrawal_saga_id = :sagaId;
```
**커밋 결과 불명확(unknown commit)** 상태는 단순 실패로 취급하지 않는다. DB 커밋이 성공했지만 응답만 유실되었을 가능성이 있으므로 `processed_event` 및 결과 Outbox를 먼저 확인하고, 성공 여부를 판별하지 못했다면 임의로 EV-04를 발행하지 않는다. 재처리 또는 관리자 조사가 필요하다.

### 4.2 Kafka 재시도와 Saga 보상 처리

EV-02(`user.withdrawal.requested`)의 **매치 정리 실패**에는 보상 이벤트 EV-04가 필요하지만, **커밋 결과 불명확·보상 기록 실패** 등에는 임의로 보상할 수 없다. 아래는 팀 공통 Kafka 명세의 예외로 적용할 구현 권고안이다.

**처리 규칙**

1. EV-02 정리 처리와 후속 Outbox INSERT는 하나의 match DB 트랜잭션에서 수행한다. 예외 발생 시 트랜잭션 전체를 롤백하며, 성공 커밋 여부가 불명확하면 실패로 단정하지 않는다.
2. 롤백이 확인된 정리 오류에는 **최초 처리 1회 + 1초 간격 재시도 3회**를 적용한다. 오류 종류에 따라 즉시 DLT로 보낼 항목(예: 메시지 역직렬화 오류)도 별도 분류한다.
3. 재시도 소진 후 먼저 `processed_event`와 결과 Outbox로 **기존 성공·실패 처리 기록이 이미 커밋됐는지 확인**한다. 이미 있다면 새 EV-04를 만들지 않는다.
4. **정리 트랜잭션의 롤백이 확인되고 처리 완료 기록이 없는 경우에만**, 별도 match DB 트랜잭션에서 `processed_event(EV-02.eventId)`와 EV-04 Outbox(`PENDING`)를 원자적으로 기록한다.
5. EV-04 Outbox 커밋이 확인되면 EV-02는 복구 처리 완료이며 **동일 원본을 DLT로 중복 전송하지 않는다**. EV-04의 실제 Kafka 발행은 Outbox 발행기가 담당한다.
6. 기존 커밋 결과가 불명확하거나 보상 기록의 커밋 여부를 안전하게 확인할 수 없으면 **EV-04를 임의로 만들지 않는다**. 실패 또는 불확실한 레코드를 `{원본토픽}.DLT`에 보존하고 운영자가 조사하도록 한다.
7. **DLT 전송 성공까지 확인**되면 원본 EV-02의 오프셋은 커밋할 수 있다. 이는 Saga 성공/보상이 아니라 **원본 메시지를 DLT로 이관했다는 뜻**이다. DLT 발행마저 실패하면 원본 오프셋을 완료 처리하지 않는다.
8. DLT에 이동한 EV-02를 나중에 재처리할 때는 IN-01을 호출하여 현재 회원 상태가 WITHDRAWAL_PENDING이고 응답의 sagaId가 해당 EV-02의 sagaId와 일치하는지 검증해야 한다. 일치하지 않는 과거 이벤트는 재처리하지 않는다. IN-01 조회에 실패하면 재처리를 보류하고 조사 대상으로 처리한다. 오래된 이벤트를 무조건 재실행하면 복원된 ACTIVE 회원의 새 매치까지 삭제할 수 있다.

**Spring Kafka 구현 확인 항목**

- EV-02 리스너에 `DefaultErrorHandler` + `FixedBackOff(1000L, 3L)`와 **사용자 정의 Recoverer**를 적용하는 방식을 기준으로 한다. 이미 `@RetryableTopic` 등을 사용한다면 중복 재시도 구성이 되지 않도록 한 방식으로 통일한다.
- Recoverer의 보상 기록은 **리스너의 실패한 정리 트랜잭션과 분리된 실제 새 트랜잭션**이어야 한다. 동일 객체 내부 `@Transactional` 자기 호출처럼 트랜잭션 프록시가 적용되지 않는 구현을 피한다.
- Recoverer는 ① 처리 이력/결과 Outbox 재조회 ② 롤백 확정 시 EV-04 기록 ③ 안전하게 처리할 수 없는 경우 DLT 전송 순으로 분기한다. DB 복구 기록이 정상 커밋됐을 때만 성공 반환한다.
- 수동 오프셋 처리라면 Listener의 AckMode와 ErrorHandler의 `commitRecovered`/`ackAfterHandle` 등 설정을 **실제 사용 방식에 맞춰 통합 테스트**한다. `MANUAL_IMMEDIATE`에서 `commitRecovered=true`를 사용하는 방식이 가능하다.
- 팀 명세의 DLT 이름은 `{토픽}.DLT`이므로, `DeadLetterPublishingRecoverer`의 목적지를 **명시적으로 지정**한다. 현재 Spring Kafka 문서의 기본 접미사 `-dlt`와 다르다. DLT 토픽을 미리 생성하고 파티션 정책도 확인한다.
- DLT 전송 성공을 확실히 확인하도록 발행 결과 검증 설정을 사용한다. DLT로 이동한 회원의 PENDING 상태가 장기간 방치되지 않도록 **로그·알림·수동 재처리 담당**을 지정한다.
- `saga.fail-cleanup=true` 시연에서는 매치 정리 롤백 → EV-04 Outbox → user-service ACTIVE 복원을 확인한다.

### 4.3 실패 주입 시연

Config `saga.fail-cleanup=true`를 사용해 EV-02의 매치 정리 단계에서 **의도적인 예외를 발생**시킨다.

- 정상 모드: `ACTIVE → WITHDRAWAL_PENDING → WITHDRAWN`

- 실패 시연: `ACTIVE → WITHDRAWAL_PENDING → ACTIVE` (재시도 및 EV-04 보상 후)

- 검증: 매치 데이터 부분 삭제 없음, Guard 값 유지, EV-04 Outbox 및 발행 로그, 회원 상태 복원.

- 실패 주입 위치는 **정리 트랜잭션 내부**로 정하고, 예외를 삼켜 정상 커밋하지 않도록 한다.

## 5. 동시성: `match_user_guard`

스키마: `match_user_guard(user_id BIGINT PRIMARY KEY NOT NULL, is_blocked BIT(1) NOT NULL DEFAULT 0)`.

`SELECT ... FOR UPDATE`는 **트랜잭션 중 DB 행 잠금**이고, `is_blocked=true`는 **트랜잭션 종료 후에도 남는 업무 상태**다. 서로 대체할 수 없다.

Guard 행이 없을 때 원자적으로 생성하는 MariaDB 예시:
```sql
INSERT INTO match_user_guard (user_id, is_blocked)
VALUES (:userId, 0)
ON DUPLICATE KEY UPDATE user_id = user_id;
SELECT user_id, is_blocked
  FROM match_user_guard
 WHERE user_id = :userId
 FOR UPDATE;
```
이 작업을 **매치 생성·참가·탈퇴 정리의 동일한 DB 트랜잭션 내에서 먼저** 수행한다. 중복 키 발생 시 기존 `is_blocked=true`를 `false`로 리셋하지 않는다.

| 작업 | Guard 사용 방식 |
| --- | --- |
| EM-01 매치 생성 | IN-01로 회원이 `ACTIVE`인지 먼저 확인 → Guard 행 잠금 → `is_blocked=false` 확인 → 매치 생성 → 커밋 시 잠금 해제. **Guard 값은 변경하지 않음** |
| EM-06 매치 참가 | IN-01로 `ACTIVE` 확인 → Guard 행 잠금 → `is_blocked=false` 확인 → 참가 처리 → 커밋 시 잠금 해제. **Guard 값은 변경하지 않음** |
| EV-02 탈퇴 정리 | Guard 행 잠금 → 회원 관련 매치·참가 정리 → `is_blocked=true` → 처리 이력 + EV-03 Outbox → 커밋 |
| 정리 실패 | 트랜잭션 롤백. 잠금 해제되고 `is_blocked`는 기존 값 유지 |

이 순서로 구현하면, 매치 API가 IN-01에서 `ACTIVE`를 확인한 직후 회원이 PENDING으로 바뀌더라도 두 작업은 Guard 잠금으로 직렬화된다. **매치 생성/참가를 완료한 뒤 탈퇴 정리가 수행되거나**, **탈퇴 정리가 먼저 완료되어 매치 생성/참가가 차단된다.** 단, EM-01·06과 EV-02 중 어느 한 경로라도 Guard 잠금을 생략하면 이 보장은 깨진다.

IN-01 명세상 `WITHDRAWAL_PENDING`과 `WITHDRAWN`은 매치 기능에서는 모두 `401 USER_WITHDRAWN`으로 취급한다. user-service가 응답하지 않으면 기존 `503 USER_SERVICE_UNAVAILABLE` 정책을 따른다. DB 행 잠금 중 외부 Feign 호출은 가급적 피한다.

## 6. Outbox 및 중복 처리

### 6.1 Outbox 공통 구조

`user_outbox`, `match_outbox`는 공유 DB 명세에 정의된 다음 컬럼을 사용한다.

`id`(PK, auto increment), `event_id`(UUID 문자열, UNIQUE), `topic`, `message_key`, `payload`(JSON), `status`(`PENDING`/`SENT`, 기본 `PENDING`), `attempt_count`(기본 0), `created_at`, `sent_at`(nullable). 조회용 인덱스 `(status, id)`.

발행기 동작:

1. `status=PENDING`인 레코드를 `id` 오름차순으로 읽는다(현재 명세의 발행 순서 규칙).
2. 저장된 `topic`, `message_key`, `payload`를 Kafka로 전송한다. 재시도 때 **새 eventId를 만들지 않는다.**
3. Kafka 발행 성공 확인(ack) **후** 해당 행을 `SENT`로 바꾸고 `sent_at`을 기록한다.
4. 실패 시 `PENDING`을 유지하고 `attempt_count`를 갱신하며 후속 발행 때 재시도한다.

**중복 발행 가능성을 전제로 한다.** Kafka 발행은 성공했지만 Outbox의 `SENT` 기록에 실패하면 재전송될 수 있다. 여러 인스턴스가 Outbox를 발행할 경우 동일 행의 동시 발행을 방지하거나 중복을 감내하는 전략이 필요하다. 소비자 멱등성이 최종 안전장치다.

### 6.2 `processed_event`

match-service는 EV-02 수신 시 `processed_event(event_id PRIMARY KEY, topic, processed_at)`로 중복 처리 여부를 판별한다. **처리 이력 INSERT와 매치 정리/결과 Outbox를 한 트랜잭션에 포함해야 한다.** Guard 행 잠금 *전* 조회가 중복이 아니어도 잠금을 얻은 *후* `SELECT ... FOR UPDATE` 등의 잠금 조회로 다시 검사해야 한다. 일반 SELECT만 반복하면 `REPEATABLE READ` 스냅샷 때문에 이전 값을 계속 볼 수 있다. PK 충돌은 중복 처리 여부를 재확인하는 신호이며, 모든 DB 오류를 멱등 성공으로 처리해서는 안 된다.

user-service의 EV-03·04에는 별도 수신 이력 테이블을 필수로 추가하지 않는다. 현재 명세의 **상태 + sagaId 조건부 UPDATE**만으로 회원 상태 전이에 대한 중복·지연 메시지를 차단한다. 단, 그 외 부수 효과가 추가되면 별도 멱등 처리 수단이 필요할 수 있다.

### 6.3 Kafka ack·DLT와 Outbox의 책임 구분

- Kafka **소비 ack/원본 오프셋 완료**: 원본 레코드에 대한 처리를 끝냈다는 의미. 정상 처리·보상 Outbox 커밋뿐 아니라 **DLT 안전 이관** 후에도 가능하며, 반드시 Saga가 완료됐다는 뜻은 아니다.

- Outbox **`SENT`**: Kafka에 이벤트 발행이 성공했다는 의미.

- Saga **완료**: user-service가 EV-03을 적용해 회원을 `WITHDRAWN`으로 변경한 시점.

- **DLT**: 자동 처리 실패 레코드를 조사/재처리하기 위한 경로. DLT 이동만으로 회원 상태가 보상되지 않는다.

## 7. 인증·응답 정책

| 회원 상태 | 인증 관련 정책 | 일반 매치 기능 |
| --- | --- | --- |
| `ACTIVE` | 기존 정책 | 허용(기존 조건 충족 시) |
| `WITHDRAWAL_PENDING` | Refresh Token 유지, USR-05 재발급 및 USR-06 조회 허용. **단, USR-06은 상태 값을 반환하지 않음** | IN-01과 Guard 정책에 따라 생성·참가 차단 |
| `WITHDRAWN` | Refresh Token 재발급 거부, 저장된 토큰 삭제, USR-06 접근 거부 | 차단 |

**구현 유의:** Gateway의 JWT 검증 성공은 회원 DB의 `ACTIVE` 여부를 뜻하지 않는다. 상태에 따른 제한이 필요한 API는 user-service 조회 또는 해당 서비스의 정책 검증이 별도로 필요하다. 특히 토큰 재발급에서는 Redis의 토큰 존재 여부뿐 아니라 현재 회원 상태도 확인해야 한다. **매치 이외의 수정 API도 PENDING에서 제한할지 별도 정해야 한다.** 현재 Guard로 보장하는 것은 EM-01·06의 신규 매치 생성·참가와 EV-02 정리의 직렬화이며, 모든 서비스 API의 접근 차단이 자동으로 구현되지는 않는다.

USR-04는 `202 Accepted`(본문 없음)만 반환한다. 프론트의 완료 폴링과 별도 상태 조회 API는 **이번 구현에 포함하지 않는다**. 공유 명세의 `UserInfoResponseDto`에는 현재 `status` 필드가 없으므로, **USR-06을 호출할 수 있다는 사실만으로 클라이언트가 정확한 회원 상태를 확인할 수 있는 것은 아니다.** 사용자가 탈퇴 완료를 어떤 방식으로 직접 확인하는지는 필요할 때 별도 UX/계약으로 결정한다. 따라서 USR-05에서 PENDING 토큰 재발급을 허용하는 것은 **보상으로 ACTIVE로 돌아갈 때 기존 인증을 유지하기 위한 정책**으로도 이해해야 하며, 현재 API만으로 탈퇴 처리 상태 확인 기능이 제공되는 것은 아니다.

## 8. 구현 분담·순서

| 단계 | 주 담당 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| 1 | 송석주 | `WITHDRAWAL_PENDING`, `withdrawal_saga_id`, USR-04, `user_outbox`, EV-02 발행기 | 202 후 user DB에 PENDING+EV-02 Outbox가 함께 남음 |
| 2 | 민경완 (김예림과 협의) | match-service 측 Saga: EV-02 소비·매치 정리, Guard, `processed_event`, `match_outbox`, EV-03 발행기 | 정리 + 차단 + 결과 Outbox 원자적 커밋 |
| 2-1 | 김예림과 협의 (구현 담당 미확정) | 기존 EM-01·06에 Guard 잠금·차단 검사 연계 | 매치 생성·참가와 Saga 정리가 충돌하지 않음 |
| 3 | 송석주 | EV-03 수신 및 회원 상태 확정, 토큰 정책 | `WITHDRAWN` + Saga ID 제거, 중복 이벤트 무해 |
| 4 | 민경완 · 송석주 (김예림 검토) | 재시도·복구 핸들러, EV-04, `saga.fail-cleanup` | 실패 후 `ACTIVE` 복원, Guard/매치 원상 유지 |
| 5 | 공동 | 통합 테스트·로그·DLT 운영 점검 | 정상·실패·중복·발행 장애·동시성 확인 |

WBS 매핑: **W-22 = 성공 경로(송석주 · 민경완)**, **W-32 = 실패 보상과 고의 실패 시연(민경완 · 송석주)**. 김예림은 기존 매치 도메인 및 Guard 연계 검토가 필요하지만 **Saga 구현 전체가 김예림 담당으로 확정된 것은 아니다.** EM-01·06 수정의 세부 구현 담당은 담당자 간 확인한다. DB/이벤트 스키마는 W-02 공유 명세를 참조한다.

## 9. 통합 검증 체크리스트

- [ ] **정상 탈퇴:** `ACTIVE → WITHDRAWAL_PENDING → WITHDRAWN`, 회원이 소유하거나 참가한 매치 관련 데이터 정리, Guard=true, EV-03 수신.

- [ ] **실패 보상(TC-25):** `saga.fail-cleanup=true`로 재시도 소진 → EV-04 → `ACTIVE`, 매치 부분 삭제 없음, Guard 기존 값 유지.

- [ ] **중복 EV-02:** 같은 `eventId` 재수신(순차 및 동시 처리) 시 Guard 잠금 뒤 중복 재확인, 매치 중복 정리·결과 이벤트 재생성 없음.

- [ ] **중복·지연 EV-03/04:** 이전 `sagaId` 이벤트가 현재 회원 상태를 덮어쓰지 않음.

- [ ] **Kafka 발행 실패:** Outbox는 PENDING 유지 → Kafka 회복 후 동일 `eventId`/payload로 발행 → SENT.

- [ ] **DB 커밋 후 소비 ack 전 장애:** EV-02 재전달 시 `processed_event`로 이미 처리한 것으로 판단.

- [ ] **매치 생성·참가 경합:** EV-02 정리와 EM-01·06을 병렬 실행해 정리 이후 해당 회원의 신규 매치/참가가 남지 않음.

- [ ] **토큰:** PENDING에서 재발급·내 정보 조회 가능, WITHDRAWN에서는 재발급·접근 차단.

- [ ] **DLT/복구 실패:** EV-04를 저장하지 못했을 때 DLT 발행 성공 후에만 원본 오프셋 완료. 오래된 EV-02의 수동 재처리 시 sagaId 확인. PENDING 회원 방치 여부 점검.
- [ ] **토큰 삭제 실패:** 회원 상태가 WITHDRAWN으로 확정됐으나 Redis 삭제에 실패한 뒤에도 refresh 거부 및 정리 재시도 검증.
- [ ] **DLT 토픽 이름:** 명세의 `.DLT`로 실제 전송되는지 확인(기본 `-dlt`를 사용하지 않도록 목적지 지정).

## 10. 구현 착수 전 확인 사항

1. **EV-02 최종 실패와 DLT 예외:** §4.2의 분기를 구현 권고안으로 채택했다. **팀 전체 확정 전** 공유 Kafka 공통 명세에 "EV-02는 롤백 확정 시 EV-04 Outbox를 우선 기록하며, 복구 불가 시 DLT로 안전 이관"이라는 예외를 반영하고 ErrorHandler/Recoverer/ack 설정을 통합 테스트해야 한다. **최우선.**
2. **WITHDRAWAL_PENDING의 API 제한 범위:** USR-05·06 허용, EM-01·06 차단은 정해졌지만 그 외 프로필 수정·AI 등 어떤 API를 막을지, Gateway/각 서비스 중 누가 검사할지 담당자가 정한다.
3. **탈퇴 완료의 수동 확인 방식:** 이번 범위에 폴링·별도 조회 API가 없고 USR-06 응답 DTO에 `status`가 없다. 프론트의 202 안내 문구와 실제 확인 수단을 혼동하지 않는다.
4. **실패 보상 사유(`reason`)와 운영 재처리:** 롤백 확인 가능한 정리 실패와 커밋 불확실성을 구분한다. EV-04 reason에는 내부 오류 코드/요약만 담고 비밀번호·개인정보·스택 트레이스를 넣지 않는다. DLT 메시지 확인·수동 재처리 담당을 정한다.
5. **매치 삭제의 실제 연관관계:** 매치 소유자 삭제 시 타 회원 참가 관계 및 FK 제약까지 포함하는 구체적인 정리 순서는 구현 중 엔티티·DB 구조와 대조한다.

---

### 관련 명세

- 공유 시트: https://docs.google.com/spreadsheets/d/1A8f3t_F_qhmwYJm5k1pHbWqDvcinRsun/edit

- API 명세: USR-04·05·06 / EM-01·06 / IN-01

- 이벤트 명세: EV-02·03·04 및 Kafka 공통 재시도 규칙

- DB 정의: `user.status`, `user.withdrawal_saga_id`, `user_outbox`, `match_outbox`, `processed_event`, `match_user_guard`

- WBS: W-22, W-32 / 테스트: TC-04, TC-25

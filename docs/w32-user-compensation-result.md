# W-32 user-service 탈퇴 실패 보상 검증

검증일: 2026-11-04
개발 기준: develop `0b48c25`
작업 브랜치: `codex/user-w32-compensation`

## 구현

- `match.withdrawal.failed`(EV-04)를 `user-service` 그룹으로 구독한다.
- 본문은 eventId, occurredAt, sagaId, userId, reason 필드를 사용한다.
- 현재 상태가 WITHDRAWAL_PENDING이고 withdrawal_saga_id가 일치할 때만 조건부 UPDATE로 ACTIVE 복원, Saga ID 제거, updated_at 갱신을 수행한다.
- withdrawn_at 및 Refresh Token은 변경하지 않는다.
- 반영 0건은 중복·과거 Saga·비대상 회원으로 보고 상태를 바꾸지 않고 `[KAFKA]` 무시 로그를 남긴다.
- DB 및 역직렬화 예외를 숨기지 않는다. EV-03과 같은 공통 Kafka 오류 처리기(1초 간격 재시도 3회, 이후 해당 토픽 .DLT)를 사용한다.
- 실패 주입과 EV-04 발행은 match-service 담당 범위다. 실패 후 자동 탈퇴 재요청은 구현하지 않는다.

## 자동 테스트

```powershell
cd backend
.\gradlew.bat :user-service:test :user-service:bootJar --console=plain
```

결과: BUILD SUCCESSFUL, 테스트 68건 / 실패 0 / 오류 0.
추가 테스트 13건: JPA 기반 보상 처리 9건, 리스너 단위 테스트 4건.

- 정상 복원 및 회원 기본정보 보존
- 중복 실패 이벤트 무시
- ACTIVE 복원 뒤 새 Saga 시작 시 이전 실패 이벤트 무시
- 다른 Saga의 실패 이벤트 무시
- 성공 확정 뒤 늦은 실패 이벤트로 회원이 복원되지 않음
- 실패 복원 뒤 늦은 성공 이벤트로 회원이 탈퇴되지 않음
- ACTIVE 회원·미존재 회원 무시
- 누락·잘못된 필수 식별값 거절
- EV-04 역직렬화·위임, 무시 처리, DB 예외 전파, 잘못된 JSON 거절

JPA 자동 테스트 DB는 H2 MariaDB 모드이며, 아래 별도 수신 검증은 실제 MariaDB를 사용했다.

## 실제 Kafka / MariaDB / Redis 수신 검증

실행 ID: `a6f37d7740e6`, 테스트 회원 ID: `17`.

별도 회원을 실제 user-service API로 가입·로그인시킨 뒤, 테스트 fixture로 해당 회원의 상태와 Saga ID를 PENDING으로 설정했다. EV-04는 Kafka 콘솔 프로듀서로 직접 발행했다. 기존 회원은 변경하지 않았다.

| 검증 항목 | 결과 |
| --- | --- |
| 실제 EV-04 소비 후 MariaDB 회원 상태 | ACTIVE |
| withdrawal_saga_id / withdrawn_at | NULL / NULL |
| Redis 기존 Refresh Token | 복원 전후 동일 |
| 복원 후 기존 Refresh Token 재발급 | 200 |
| 복원 후 로그인 | 200 |
| 같은 EV-04 재전송 | ACTIVE 유지 |
| 새 PENDING Saga에 이전 EV-04 재전송 | 새 Saga·PENDING 유지 |
| 새 Saga와 일치하는 EV-04 전송 | ACTIVE 복원 |

검증용 회원은 ACTIVE로 남겼다. 토큰·비밀번호·개인 설정은 결과 문서에 기록하지 않았다.

## 검증 범위와 후속 작업

- 실제 수신 검증은 user-service 8001 API와 Kafka·MariaDB·Redis를 연결했다. Gateway 경유 검증은 이번 실행 범위에 포함하지 않는다.
- EV-02 발행 → 실제 매치 정리 실패·롤백 → EV-04 발행 전체 흐름을 검증한 것은 아니다.
- 양쪽 W-32 PR 병합 후 match-service의 실패 주입으로 TC-25 전체 보상 흐름과 매치·참가 데이터 보존을 별도로 확인한다.
- 동일 user-service를 실행할 때 local 개인 설정은 해당 PC의 Docker DB 포트와 맞아야 한다. 이번 PC는 13306을 사용했으며 공유 설정에는 반영하지 않았다.
- #58 DB 마이그레이션 SQL은 사용자 요청에 따라 추가하지 않았다.

## 2026-11-04 후속 전체 검증 완료

PR #56·#67 병합 후 실제 Gateway 실패 보상, 매치·참가 데이터 보존, 정상 탈퇴 회귀 및 외부 테스트 10건 검증을 완료했다. 상세 조건과 결과는 [W-32 전체 통합 검증](w32-integration-result.md)을 참조한다.

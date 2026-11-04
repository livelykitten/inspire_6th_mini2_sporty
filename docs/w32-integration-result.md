# W-32 회원 탈퇴 실패 보상 전체 통합 검증

검증일: 2026-11-04
기준 커밋: `28addb0` (develop, match-service PR #56 및 user-service PR #67 병합 후)
작업 브랜치: `codex/w32-integration`

## 실행 구성

- Gateway 8000 → user-service 8001 / match-service 8002, Eureka 8761, Config 8888.
- 실제 Docker MariaDB의 user_db / match_db, Redis, Kafka를 사용했다.
- 앱 서버는 로컬 Java 17 프로세스로 실행했다. 전체 앱 Compose 및 브라우저 화면 검증은 이번 범위가 아니다.
- 이 PC는 로컬 MariaDB와의 포트 중복을 피하려 user_db 호스트 포트 13306을 사용했다. 컨테이너 내부는 user-db:3306이며 공유 설정은 변경하지 않았다.
- Config 실행 환경에 저장소 예시와 일치하는 로컬 ENCRYPT_KEY를 적용해 복호화를 확인했다. Gateway/user의 복호화된 JWT 키가 일치함을 확인했고, Gateway에 JWT 키를 직접 전달하는 우회 없이 로그인과 인증 호출을 검증했다. 실제 키는 출력·커밋하지 않았다.

## 전체 흐름 결과

성공 실행 ID: `213195949510`

| 항목 | 결과 |
| --- | --- |
| Gateway 회원가입 / 로그인 | 201 / 200 |
| 본인 소유 매치 개설 및 다른 회원 매치 참가 | 201, 다른 매치 정원 2명 충족·CLOSED |
| 실패 주입 | match-service `--saga.fail-cleanup=true` |
| Gateway 탈퇴 접수 | 202, 실제 user_outbox EV-02 생성 및 SENT |
| 실제 처리 시도 | 최초 1회 + 1초 간격 재시도 3회, attempt 1~4 로그 확인 |
| 매치 정리 실패 | 각 시도 트랜잭션 롤백 |
| 보상 이벤트 | EV-04 `MATCH_CLEANUP_FAILED`, EV-02와 동일 Saga·회원, 별도 eventId, match_outbox SENT 1건 |
| 중복 결과 방지 | EV-02 processed_event 1건, 실패 Saga의 EV-03 없음 |
| 회원 복원 | ACTIVE, withdrawal_saga_id NULL, withdrawn_at NULL |
| 매치·참가 데이터 보존 | 대상 매치 2개와 참가 행 전체 컬럼의 전후 조회 결과 동일 |
| Guard 보존 | user_id·is_blocked 전후 동일 (차단되지 않음) |
| Redis Refresh Token | 보상 전후 기존 값 동일 |
| 기존 RT 재발급 / 기존 AT 내 정보 | Gateway 각각 200 / 200 |
| 복원 후 매치 API / 재로그인 | 200 / 200 |
| 같은 EV-02·EV-04 재전송 | ACTIVE·데이터 유지, 추가 EV-04 없음 |

실제 요청은 Gateway로 보냈다. Saga 상태 확인은 내부 IN-01과 DB를 사용했고, 시설 준비는 고유 ID의 매치 서비스 소유 시설 스냅샷 fixture를 INSERT했다. 상태 전이는 SQL로 만들지 않고 실제 탈퇴 API와 양쪽 서비스를 통해 처리했다.

## 실패 주입 해제 후 정상 경로 회귀

실패 주입을 `--saga.fail-cleanup=false`로 해제하고 match-service를 재기동했다. 보상된 동일 회원이 다시 실제 탈퇴 요청을 보냈다.

| 항목 | 결과 |
| --- | --- |
| 같은 회원의 탈퇴 재시도 | 202, 이전과 다른 새 Saga ID 생성 |
| EV-02 → 정상 매치 정리 → EV-03 | WITHDRAWN 확정, Saga ID 제거 |
| EV-03 본문 | deletedMatchIds에 본인 매치, leftMatchIds에 다른 회원 매치 포함 |
| 본인 소유 매치 | 상세 API 404 |
| 다른 회원 매치 | 보존, 참가 인원 2 → 1, CLOSED → RECRUITING |
| 본인 참가 행 / Guard | 0건 / is_blocked=1 |
| Redis RT | 삭제 |
| 기존 AT 내 정보 | 401 USER_ALREADY_WITHDRAWN |
| 기존 RT 재발급 / 로그인 | 각각 401 |
| 이전 Saga EV-04 및 같은 EV-03 재전송 | WITHDRAWN 유지, RT 복원 없음 |

이 흐름에서 총 43개 PASS 검증을 완료했다.

## 자동 테스트 및 외부 실패 경로

- 병합된 user-service: 전체 테스트 68건 통과, 실패·오류 0건. bootJar 빌드 성공.
- match-service: 리뷰 당시 커밋 3e0e088에서 전체 테스트 174건 통과(외부 테스트 10건 제외). 현재 기준 커밋과 match-service 디렉터리의 변경이 없음을 확인했다.
- 추가로 현재 브랜치에서 `scripts/integration/w32-external-check.py`를 실행해 외부 테스트 10건 전부 통과(실패·오류·제외 0건).
- 외부 테스트는 별도 w32_test_* DB·계정과 고유 Kafka 토픽·그룹을 사용한다. 기존 match_db/user_db를 초기화하지 않으며 실행 후 생성한 DB·계정을 정리한다.
- 실제 Kafka 리스너의 최초 1회+재시도 3회, 보상 Outbox 커밋 후 오프셋 완료, IN-01 장애의 DLT 이관, DLT 원문 key/value 보존, 잘못된 JSON 즉시 DLT 및 필수 식별값 누락의 4회 시도 경로를 확인했다.
- 외부 테스트의 IN-01 제공자는 Mock이다. 실제 user-service와의 연결은 앞의 Gateway 전체 흐름에서 별도로 검증했다.
- 커밋 결과 불명확·DLT 발행 실패 등은 테스트 대역과 예외 주입으로 검증하며 실제 DB/Kafka 프로세스 장애를 주입한 것은 아니다.
- 테스트 실행에만 SPRING_CLOUD_CONFIG_ENABLED=false를 적용해 전용 테스트 DB 설정이 개발 Config 설정에 영향받지 않게 했다. 실제 앱 서비스의 Config 연결은 켠 상태로 유지했다.

## W-22 및 Config 후속 회귀

Config 복호화 기동 후 기존 `scripts/integration/w22-integration-check.ps1`도 그대로 재실행해 통과했다.

- 실행 ID: `3470d1bf4361`.
- Gateway를 통한 정상 탈퇴, EV-02/EV-03, 매치 정리·다른 회원 매치 모집 재개, Redis RT 삭제 및 탈퇴 후 인증 차단 통과.
- 실제 동시 탈퇴 요청: 202 1건 + 409 WITHDRAWAL_ALREADY_PENDING 1건, EV-02 Outbox 1건.
- 회원 22·23·24, 매치 45·46, 시설 스냅샷 9261104110647을 사용했다.
- Gateway에 JWT 키를 직접 전달하지 않는 Config 기본 기동 조건에서 확인했다.
- 종료 시 8000·8001·8002·8761·8888의 health가 모두 UP이며, match-service 실행 인수는 saga.fail-cleanup=false다.

## 테스트 데이터와 로그

- 탈퇴 회원: 20, 잔존 매치 소유 회원: 21.
- 본인 매치: 43 (정상 탈퇴 회귀에서 삭제), 다른 회원 매치: 44 (보존).
- 시설 스냅샷: 9261104110230.
- 실패 Saga: `651b91f3-1698-46b8-b89e-df4ba3323f83`.
- 정상 재시도 Saga: `2136e41a-d96e-4f0a-abf6-0018958f0f6a`.
- 실패 EV-02 eventId: `5289d48b-1a31-4a3a-a749-f608b66f5b8e`.
- 실패 시도 로그: 11:02:37.820 / 11:02:38.974 / 11:02:40.150 / 11:02:41.312, 동일 user.withdrawal.requested partition 0 offset 10.
- 보상 기록 커밋 로그: 11:02:41.353.

초기 검증 스크립트가 참가 테이블 PK를 id로 가정해 탈퇴 요청 전 데이터 비교 단계에서 실패했다. 실제 컬럼 match_participant_id로 수정한 뒤 전체 재실행에 성공했다. 서비스 구현 결함은 발견하지 않았다. 초기 실행의 고유 테스트 회원·매치도 검사 목적으로 남아 있으며 기존 데이터를 초기화하지 않았다.

## 재실행

`scripts/integration/w32-integration-check.ps1`은 PowerShell 7을 사용한다. 선행 조건은 최신 두 서비스·Gateway·Config·Eureka 및 DB·Redis·Kafka의 정상 기동, Config JWT 키 일치, match-service의 실패 주입 활성화다.

`-RestoreNormalMode`에는 match-service의 실패 주입을 끄고 재기동한 뒤 정상 기동을 확인하는 scriptblock을 전달한다. 실행 환경(IDE/로컬 Java/Compose)에 맞춰 호출자가 제공한다. 이 콜백 이후 동일 회원의 정상 탈퇴 회귀를 이어서 수행한다. 콜백이 오류를 보고할 때는 예외를 발생시켜야 한다.

검증 실행은 반드시 try/finally로 감싸고, 중간에 실패하더라도 실패 주입을 해제한다. 이번 실행에서도 초기 스크립트 오류 시 자동 해제한 뒤 재실행했고, 최종 성공 후에는 정상 모드로 남겼다.

## 범위

- 이번 문서는 실제 Gateway 경유 W-32 전체 보상 및 정상 탈퇴 회귀를 다룬다.
- 일반 화면 동작·프론트 교차 테스트, 시설 API 동기화 및 Zipkin 추적은 별도 범위다.
- 테스트 회원·매치·시설을 새로 만들며 기존 데이터를 초기화하지 않는다. 토큰·비밀번호·개인 설정은 출력하거나 문서에 저장하지 않는다.
- #58 마이그레이션 SQL은 사용자 요청에 따라 추가하지 않았다.

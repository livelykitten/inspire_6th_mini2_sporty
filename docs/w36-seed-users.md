# W-36 시연 회원 시드

PowerShell 7 스크립트: `scripts/demo/seed-users.ps1`.
Python·별도 패키지 설치 없이 Gateway의 실제 회원가입·로그인·내 정보 API만 사용한다. 데이터베이스 초기화나 직접 INSERT를 수행하지 않는다.

## 시연 계정 목록

시연 전용 공개 비밀번호는 모두 `1234567890`이다. 실제 사용자 데이터에 사용하지 않는다.

| 역할 키 | 이메일 | 닉네임 | 성별 | 지역 | 선호 종목 |
| --- | --- | --- | --- | --- | --- |
| owner | a@b.c | 테스트유저1 | MALE | MAPO | FUTSAL |
| participant | b@c.d | 테스트유저2 | MALE | GANGNAM | FUTSAL, BASKETBALL |
| withdrawal | withdraw@demo.test | 탈퇴시연유저 | MALE | MAPO | FUTSAL |

## 실행 명령

저장소 루트의 PowerShell 7에서 실행한다. BASE_URL은 Gateway 주소이며, 로컬·배포 모두 나머지 동작은 같다.

```powershell
& ./scripts/demo/seed-users.ps1 -BASE_URL http://localhost:8000
```

BASE_URL 환경변수도 사용할 수 있다.

```powershell
$env:BASE_URL = 'http://localhost:8000'
& ./scripts/demo/seed-users.ps1
```

스크립트는 올바른 비밀번호로 로그인되는 계정을 재사용한다. 로그인 401일 때만 회원가입을 시도하고, 이메일/닉네임 중복이면 명확히 중단한다. 비밀번호 불일치·탈퇴 상태·프로필 불일치를 자동으로 수정하지 않는다. 연결 오류와 5xx를 계정 미존재로 취급하지 않는다. 중간에 멈춰도 먼저 생성된 회원을 지우지 않으며 재실행 시 재사용한다.

반복 로그인은 일반 로그인 정책대로 Redis Refresh Token을 교체할 수 있다. 회원·프로필·선호 종목을 중복 생성하거나 기존 매치를 삭제하지는 않는다.

## 탈퇴 장면 재촬영

기본 탈퇴 계정은 실제 탈퇴가 완료되면 재사용할 수 없다. 새 식별자를 명시해 새 탈퇴 계정만 추가한다. 일반 계정 2개는 그대로 재사용한다.

```powershell
& ./scripts/demo/seed-users.ps1 -BASE_URL http://localhost:8000 -WithdrawalId take2
```

이 경우 탈퇴 계정은 `withdraw-take2@demo.test`, 닉네임은 `탈퇴시연유저_take2`다. 식별자는 영문 소문자·숫자·하이픈, 최대 20자다. 같은 식별자를 다시 쓰면 같은 계정을 재사용한다. 탈퇴한 계정·Guard를 복원하거나 기존 기록을 초기화하지 않는다.

## 매치 시드 연결 계약

회원 담당 범위는 세 계정의 생성·로그인 및 인증 정보 반환까지다. 매치 담당은 `-OnUsersReady` callback을 받아 같은 프로세스에서 매치 생성·참가를 이어서 구현할 수 있다. callback은 세 계정 모두 검증된 뒤에만 호출된다.

```powershell
& ./scripts/demo/seed-users.ps1 -BASE_URL http://localhost:8000 -OnUsersReady {
    param($accounts)
    # $accounts.owner.UserId / .AccessToken
    # $accounts.participant.UserId / .AccessToken
    # $accounts.withdrawal.UserId / .AccessToken
    # 이 위치에서 매치 담당 스크립트를 호출한다. 토큰을 출력/파일 저장하지 않는다.
}
```

각 항목은 Role, Email, UserId, AccessToken, Nickname, District, SportTypes를 제공한다. Access Token은 실행 중 메모리로만 전달하며 기본 실행에서 출력하지 않는다. Refresh Token은 매치 시드에 전달하지 않는다. 매치 시드의 중복 실행 보장은 매치 담당 구현·연결 검증 범위다.

## 완료 검증

2026-11-04 최신 develop `8de4c71`에서 별도 빈 회원 DB와 별도 Redis DB를 사용해 실제 Gateway `8000`으로 검증했다.

- 최초 실행: 3계정 모두 로그인 200·인증된 내 정보 조회 200. 회원 3행, 프로필 3행, 선호 종목 4행.
- 회원 서버 재기동 후 재실행: 세 계정 ID 동일, 회원·프로필·선호 종목의 전체 행 내용 동일.
- `-WithdrawalId take2`: 일반 계정 유지, 새 탈퇴 계정만 추가해 회원 4행. 동일 식별자 재실행 시 전체 행 내용 동일.
- 검증 DB에서만 탈퇴 상태 fixture를 설정한 뒤 동일 식별자 실행: 충돌로 중단, 상태·비밀번호·프로필 보존, 매치 callback 미호출. 새 식별자 `take3`는 정상 생성.
- 회원 시드로 탈퇴 Outbox가 생성되지 않았으며 기존 `user_db` 회원 수 보존 확인. 검증용 DB·계정·Redis 키 정리 후 기존 회원 서버 정상 복원.
- 현재 로컬 `user_db`에도 실제 API로 시연 회원을 준비하고 두 번 실행했다. owner=25, participant=26, withdrawal=27이며 모두 생성 후 재사용 확인. 이 ID는 로컬 확인값이므로 다른 환경에서는 로그인 응답의 UserId를 사용한다.

매치 생성·참가 시드와 연결한 전체 시연 검증은 매치 담당 연결 후 수행한다. 배포 환경 실행은 아직 검증하지 않았다.

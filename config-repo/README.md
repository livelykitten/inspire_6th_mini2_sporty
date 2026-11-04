# config-repo

Config 서버(`config-service`, 8888)가 서비스에 나눠 주는 설정 파일 모음 (native 방식, W-05 · W-24 · W-25).

| 파일 | 받는 서비스 |
|---|---|
| `application.yml` | 전 서비스 공통 |
| `gateway-service.yml` | 게이트웨이 (라우트 · CORS · jwt.secret) |
| `user-service.yml` · `match-service.yml` · `facility-service.yml` · `ai-service.yml` | 각 서비스 (`spring.application.name` 과 같은 이름) |
| `{서비스}-dev.yml` | 배포(dev 프로필)에서만, 비밀값은 `${환경변수}` (Secrets Manager) |

## 서비스 쪽 application.yml 과의 관계
- 같은 키가 둘 다 있으면 **Config 서버 값이 이긴다**. 서비스 쪽 값은 Config 서버가 꺼져 있을 때 쓰는 기본값으로 남겨 둔다.
- 설정을 바꿀 때는 이 폴더 파일을 고친다. 서비스 쪽만 고치면 Config 서버가 켜져 있을 때 반영되지 않는다.

## 비밀값 규칙 (W-25, 요구사항 NFR-03 · OPT-02)
- 이 폴더에는 평문 비밀값을 넣지 않는다.
- **DB 비밀번호 3개 · JWT 키는 `'{cipher}...'`** 로 넣었다. Config 서버가 `ENCRYPT_KEY` 로 풀어서 서비스에 넘긴다. `ENCRYPT_KEY` 는 IDE · Gradle 실행이면 `backend/.env`, Compose 면 루트 `.env` 에 둔다 (#61, 두 `.env.example` 에 같은 값).
- user · gateway 는 같은 JWT 암호문을 받으므로 키를 따로 맞출 필요가 없다.
- 지금 암호문은 `.env.example` 의 **로컬 전용 키 · 로컬 전용 값**으로 만든 것이다 (공개돼 있어도 되는 값).
- 개인 API 키(`OPEN_AI_KEY` · `PUBLIC_DATA_API_KEY`)는 로컬 키가 공개라 암호문으로 넣어도 풀린다 → `${환경변수}` 로 두고 각자 `.env` 에.
- 배포 비밀값은 AWS Secrets Manager 로 받는다 (docs/deploy.md). **배포는 dev 프로필(`{서비스}-dev.yml`)에서 `${DB_PASSWORD}` · `${JWT_SECRET_KEY}` 환경변수로 덮어쓴다** (#38). 위 암호문은 Config 값이라 환경변수보다 우선하므로, 덮어쓰지 않으면 배포에서도 로컬 값이 쓰인다.
- DB 비밀번호 암호문은 compose.yaml 의 DB 계정(user_svc · match_svc · facility_svc)에 맞춰져 있다. 비밀번호가 다른 DB 를 쓰려면 암호문을 바꾸지 말고 compose.yaml 의 DB 를 쓴다.

## 암호화하는 법
Config 서버를 띄운 상태(`backend/.env` 또는 루트 `.env` 에 `ENCRYPT_KEY` 있음)에서:

```bash
curl -s -X POST http://localhost:8888/encrypt -H "Content-Type: text/plain" -d "넣을 값"
```

나온 글자를 따옴표째 넣는다.

```yaml
spring:
  datasource:
    password: '{cipher}2f1bb2527505f0f7...'
```

- `ENCRYPT_KEY` 가 다르면 풀리지 않는다. 배포용 키는 디코 DM 으로만 주고받는다.
- `/encrypt` · `/decrypt` 는 Config 서버에 직접 붙어야 쓸 수 있다 (게이트웨이에 라우트 없음).

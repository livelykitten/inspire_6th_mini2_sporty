# config-repo

Config 서버(`config-service`, 8888)가 서비스에 나눠 주는 설정 파일 모음 (native 방식, W-05 · W-24 · W-25).

| 파일 | 받는 서비스 |
|---|---|
| `application.yml` | 전 서비스 공통 |
| `gateway-service.yml` | 게이트웨이 (라우트 · CORS · jwt.secret) |
| `user-service.yml` · `match-service.yml` · … | 각 서비스 (`spring.application.name` 과 같은 이름) |
| `{서비스}-dev.yml` | 배포(dev 프로필)에서만, 비밀값은 `'{cipher}...'` |

## 비밀값 규칙
- 이 폴더에는 평문 비밀값을 넣지 않는다. 로컬은 `${환경변수}` 로 두고 값은 각자 `.env` 에 있다.
- 배포 값은 암호화해서 넣는다. Config 서버가 `ENCRYPT_KEY` 로 풀어서 서비스에 넘긴다.

## 암호화하는 법
Config 서버를 띄운 상태(`.env` 에 `ENCRYPT_KEY` 있음)에서:

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

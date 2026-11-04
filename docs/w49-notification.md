### 운영자 메일 알림 설정 (notification-service)

`notification-service`는 Kafka의 `match.withdrawal.failed` 및 `*.DLT` 토픽을 구독하여 운영자에게 이메일을 발송합니다.

메일 발송에는 Gmail SMTP를 사용하며, Google 계정의 앱 비밀번호가 필요합니다.

**1. Gmail 앱 비밀번호 발급**

1. 메일 발송에 사용할 Google 계정에서 2단계 인증을 활성화합니다.
2. [Google 앱 비밀번호 설정](https://myaccount.google.com/apppasswords)에 접속합니다.
3. `Sporty Notification` 등의 이름으로 앱 비밀번호를 생성합니다.
4. 생성된 16자리 앱 비밀번호를 복사합니다.

**2. 환경변수 설정**

프로젝트 루트의 `.env` 파일에 다음 항목을 추가합니다.

```dotenv
SMTP_USERNAME=sender@gmail.com
SMTP_APP_PASSWORD=your-app-password
ADMIN_EMAIL=admin@example.com
```

- `SMTP_USERNAME`: 알림 메일을 발송할 Gmail 계정
- `SMTP_APP_PASSWORD`: 해당 계정에서 발급한 Google 앱 비밀번호
- `ADMIN_EMAIL`: 알림 메일을 수신할 운영자 이메일 주소 (발송 계정과 달라도 됨)

**3. 실행**

Docker Compose 환경에서 다음 명령으로 서비스를 실행합니다.

```bash
docker compose -f compose.yaml -f compose.app.yaml up -d --build notification-service
```

실행 후 다음 주소에서 헬스체크 상태를 확인할 수 있습니다.

`http://localhost:8010/actuator/health`
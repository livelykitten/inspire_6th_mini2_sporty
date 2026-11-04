# AWS 1단계 배포 런북 (W-35): EC2 1대 + RDS 3대 + ALB + CloudWatch

담당: 반정욱 (@salieri009). 구성 근거는 `docs/deploy.md`, 진행 체크는 이슈 #28.
이 폴더의 템플릿은 **이 저장소에서 문법 검증(cfn-lint)까지만** 했고 실제 생성은 해 보지 못했다. 첫 배포에서 오류가 날 수 있으므로 아래 "실패했을 때"를 먼저 읽는다.

## 구성

```text
인터넷 --HTTP 80--> ALB (public 2 AZ) --> EC2 t3.xlarge (private app, Docker Compose)
                                           nginx :80 --/api/--> gateway :8000 --> user · match · facility · ai
                                           config · discovery · kafka · redis (컨테이너)
                                           └─ JDBC 3306 --> RDS MariaDB x3 (private data, 서비스당 1대)
NAT Gateway 1개(외부 API · ECR · Secrets Manager 접근) · ECR 8개 · Secrets Manager · CloudWatch(로그 · 알람 · 대시보드)
```

| 스택 | 파일 | 내용 | 소요 |
|---|---|---|---|
| stack1 | `stack1-network-data.yaml` | VPC · 서브넷 6 · NAT · SG 3 · RDS 3 · ECR 8 · 시크릿 6 · 로그 그룹 8 · GitHub OIDC 역할 · (선택) 예산 | 약 15분 (RDS) |
| stack2 | `stack2-app.yaml` | EC2 · ALB · SNS · CloudWatch 알람 · 대시보드 | 약 5분 + 부트스트랩 10~20분 |

AZ 는 리전의 첫 두 AZ(서울은 `a`, `b`)를 쓴다. 인증서 없이 HTTP 80 으로 시연하며, 인증서가 생기면 stack2 의 `CertificateArn` 만 채운다.

## 순서

### 0. 준비
- 리전 **ap-northeast-2 (서울)** 콘솔, 권한은 CloudFormation · EC2 · RDS · IAM · ECR · Secrets Manager · CloudWatch · SNS · Budgets 를 만들 수 있어야 한다.
- EC2 가 `deploy/ec2/*.sh` 와 compose 파일을 `git clone` 하므로 **stack2 의 `GitRef` 브랜치에 이 폴더와 `deploy/` 가 들어 있어야 한다.** (통합 PR 이 머지되기 전이면 임시로 그 브랜치 이름을 `GitRef` 에 넣는다.)

### 1. stack1 (가장 먼저, RDS 가 오래 걸린다)
1. CloudFormation → **스택 생성 → 새 리소스** → 템플릿 파일 업로드 `stack1-network-data.yaml`.
2. 스택 이름 `sporty-prod-data` (stack2 가 이 이름으로 값을 가져간다).
3. 파라미터는 아래 표. 대부분 기본값이면 된다. **`CreateGithubOidcProvider`**: 계정에 GitHub OIDC 공급자가 이미 있으면 `false` 로 하고 `ExistingOidcProviderArn` 에 ARN.
4. 마지막 화면에서 **"IAM 리소스를 생성할 수 있음"** 에 체크. 실패 시 롤백 설정은 **"성공한 리소스 유지"** 를 고른다 (RDS 를 다시 기다리지 않도록).
5. `CREATE_COMPLETE` 가 되면 출력(Outputs)에서 `GithubEcrPushRoleArn` 과 `ExternalKeysSecretArn` 을 확인한다.

### 2. 외부 API 키 입력 (코드 · 저장소에 두지 않는다)
Secrets Manager 에서 설명이 "외부 API 키" 인 시크릿 → **값 보기 → 편집** → `OPEN_AI_KEY`, `PUBLIC_DATA_API_KEY` 를 입력한다 (`CHANGE_ME` 를 바꾼다). 지금 못 넣어도 서비스는 더미 값으로 뜨고 AI 호출만 실패한다. 나중에 넣고 3-B 의 재배포를 하면 된다.

### 3. 이미지 전달 방식 (둘 중 하나)
- **A. ECR (권장)**: 저장소 Settings → Secrets and variables → Actions → **Variables** 에 `AWS_ROLE_ARN` = stack1 출력 `GithubEcrPushRoleArn`, `AWS_REGION` = `ap-northeast-2` 를 만든다 (저장소 admin 권한 필요, 막히면 B). Actions 의 **image** 워크플로를 `workflow_dispatch` 로 실행하고 끝나면 이미지 태그(커밋 SHA)를 메모한다. `workflow_dispatch` 는 `image.yml` 이 기본 브랜치(main)에 있어야 보이므로, 머지 전에는 `v*` 태그를 푸시해 실행한다 (태그가 가리키는 커밋의 워크플로가 쓰인다). stack2 에서 `DeployMode=ecr`, `ImageTag=<SHA>`.
- **B. EC2 에서 직접 빌드 (대안)**: AWS 설정이 필요 없다. stack2 의 `DeployMode=build` (기본값). 첫 빌드가 10~20분 걸린다.

### 4. stack2
1. 같은 방식으로 `stack2-app.yaml` 업로드, 스택 이름 `sporty-prod-app`.
2. `Stack1Name` = `sporty-prod-data`, `GitRef`/`DeployMode`/`ImageTag` 설정. 알람 수신은 이메일(`AlarmEmail`, 선택)과 Discord(`DiscordNotify`, 기본 true) 중 원하는 것을 쓴다.
3. **IAM 리소스 생성 체크.** 완료되면 출력의 `AppUrl` 이 배포 주소다.
4. **이메일을 넣었다면** 구독 확인 메일(발신: AWS Notifications)의 링크를 누른다. 누르기 전까지 이메일 알람이 오지 않는다.
5. **Discord 로 받으려면** (웹훅 URL 은 비밀값이라 템플릿에 넣지 않는다):
   1. Discord 채널 설정 → 연동 → 웹후크 → 새 웹후크 → URL 복사.
   2. 스택 출력 `DiscordWebhookSecretArn` 의 시크릿 값을 `{"webhook_url":"<복사한 URL>"}` 로 바꾼다 (Secrets Manager 콘솔 → 값 편집, 또는 `aws secretsmanager put-secret-value --secret-id <ARN> --secret-string '{"webhook_url":"<URL>"}'`).
   3. 시험: `aws sns publish --topic-arn <AlarmTopicArn> --message '{"AlarmName":"test","NewStateValue":"ALARM","NewStateReason":"manual test","Region":"Seoul"}'` → 채널에 메시지가 오면 된다. 값이 `CHANGE_ME` 인 동안 Lambda 는 전송하지 않고 로그만 남긴다.

### 5. 부트스트랩 확인
EC2 콘솔 → 인스턴스 선택 → **연결 → Session Manager** (SSH 는 열려 있지 않다).

```bash
sudo tail -f /var/log/sporty-userdata.log      # 설치 · clone · 배포 진행
sudo bash /opt/sporty/deploy/ec2/deploy.sh status               # 컨테이너 상태 (전부 healthy 여야 함)
sudo bash /opt/sporty/deploy/ec2/deploy.sh logs gateway-service # 서비스 로그 (CloudWatch 에도 올라간다)
```

로그 마지막에 `smoke OK` 가 나오면 끝이다. 시크릿을 고쳤거나 코드를 갱신했으면 같은 서버에서 `sudo bash /opt/sporty/deploy/ec2/deploy.sh` 를 다시 실행한다 (멱등).

### 6. 동작 확인 (본인 PC)
```bash
bash deploy/ec2/smoke.sh http://<AppUrl 의 주소>
```
기동 직후 30~60초는 Eureka 반영 지연으로 503 이 날 수 있어 스크립트가 재시도한다.

### 7. 알람 시험 (장애 시연 · 증빙)
Session Manager 에서 프론트 컨테이너를 잠시 멈춘다.

```bash
sudo docker stop sporty-frontend-1        # 컨테이너 이름은 docker ps 로 확인
```
2~3분 안에 `...-alb-unhealthy-host` 알람이 ALARM 이 되고 이메일이 온다. `sudo docker start <이름>` 으로 되돌리면 OK 메일이 온다. CloudWatch → 대시보드 `sporty-prod` 화면을 캡처해 증빙에 쓴다.

### 8. 삭제 (발표 11/5 13:00 직후, 과금 정리)
**stack2 먼저, 그다음 stack1** 을 삭제한다 (stack2 가 stack1 의 export 를 쓰고 있어 반대로는 지워지지 않는다). ECR 은 `EmptyOnDelete` 로 이미지째 삭제되고, 시크릿은 삭제 대기가 걸릴 수 있다. 같은 이름 충돌은 이름을 자동으로 만들기 때문에 생기지 않는다. 삭제 뒤 콘솔의 Billing 에서 NAT · ALB · RDS 가 남아 있지 않은지 확인한다.

## 파라미터

**stack1**

| 이름 | 기본값 | 설명 |
|---|---|---|
| `ProjectName` / `EnvName` | `sporty` / `prod` | 리소스 이름 접두어. stack2 와 같게 |
| `LogRetentionDays` | 7 | 로그 보존 일수 |
| `DbEngineVersion` / `DbParameterFamily` | `11.4.13` / `mariadb11.4` | 서울 리전에서 `11.4.13` 생성 확인. 없어지면 `10.11` / `mariadb10.11` 처럼 둘 다 바꾼다 |
| `DbInstanceClass` / `DbAllocatedStorage` | `db.t4g.micro` / 20 | 인스턴스당 |
| `CreateGithubOidcProvider` / `ExistingOidcProviderArn` | `true` / 빈 값 | OIDC 공급자는 계정당 URL 하나 |
| `GithubRepo` / `GithubExtraRef` | `livelykitten/inspire_6th_mini2_sporty` / `refs/heads/develop` | ECR push 를 허용할 저장소와 추가 ref (develop · main · 태그는 항상 허용) |
| `BudgetEmail` / `MonthlyBudgetUsd` | 빈 값 / 0 | 둘 다 채우면 월 예산 알림(80% 실제 · 100% 예상)을 만든다 |

**stack2**

| 이름 | 기본값 | 설명 |
|---|---|---|
| `Stack1Name` | (필수) | stack1 스택 이름 |
| `AlarmEmail` | 빈 값 | 알람 이메일(선택). 비우면 이메일 구독을 만들지 않는다 |
| `DiscordNotify` | `true` | 알람을 Discord 로도 보낸다 (SNS → Lambda → 웹훅). 웹훅 URL 은 생성 뒤 시크릿에 입력 |
| `InstanceType` / `VolumeSizeGb` | `t3.xlarge` / 30 | 메모리 알람이 울리면 한 단계 올린다 |
| `GitRepoUrl` / `GitRef` | 저장소 / `develop` | EC2 가 clone 할 대상 |
| `DeployMode` / `ImageTag` | `build` / `latest` | `ecr` 이면 ECR 이미지를 pull |
| `CertificateArn` | 빈 값 | 있으면 443 + 80→443 리다이렉트 |

`params-stack1.example.json` · `params-stack2.example.json` 은 AWS CLI(`--parameters file://`)를 쓸 때의 예시이고, 콘솔에서는 위 표대로 입력한다.

## CloudWatch 알람 (stack2, 모두 SNS 이메일)

| 대상 | 알람 | 조건 |
|---|---|---|
| ALB | 비정상 호스트 | 2분 연속 1대 이상 |
| ALB | 대상 5xx / ALB 자체 5xx | 1분에 5건 이상 / 3건 이상 |
| ALB | 응답 시간 p95 | 3분 연속 3초 초과 |
| EC2 | 상태 검사 실패 | 2분 연속 |
| EC2 | CPU | 5분 평균 80% 초과 |
| EC2 (Agent) | 메모리 사용률 | 3분 연속 85% 초과 (OOM 선제) |
| EC2 (Agent) | 디스크 사용률 | 5분 평균 80% 초과 |
| RDS ×3 | CPU · 여유 스토리지 · 여유 메모리 · 연결 수 | 80% 초과 · 2GB 미만 · 64MB 미만 · 60 초과 |
| 로그 | 서비스 로그 `ERROR` 합산 | 5분에 20건 초과 |

대시보드 `sporty-prod` 에 ALB · EC2 · RDS 지표와 알람 상태가 모여 있다. 메모리 · 디스크 지표는 인스턴스의 CloudWatch Agent 가 올리므로 부트스트랩이 끝난 뒤부터 나온다.

## 대략 비용 (추정, 정확한 단가는 AWS 요금표로 확인)

EC2 t3.xlarge · NAT Gateway · ALB · RDS db.t4g.micro ×3 · 로그 · 시크릿을 합쳐 **하루 1만 원 안팎**으로 본다. t3 는 CPU 크레딧 초과 요금이 날 수 있으니 CPU 알람과 예산 알림을 켜 둔다. 발표 직후 삭제한다.

## 실제 배포로 확인한 것 (2026-11-03 시험, EnvName=test)

- stack1 약 10분, stack2 약 4분 + EC2 부트스트랩(이미지 빌드 포함) 약 6분 후 ALB 주소로 `smoke.sh` 9항목 통과. 시험 뒤 stack2 → stack1 삭제는 약 4분, 남은 리소스 없음.
- RDS MariaDB `11.4.13` · `db.t4g.micro` ×3, EC2 `t3.xlarge`: 컨테이너 전체 메모리 약 3.2GiB / 15.8GiB, 루트 디스크 5.3GB / 30GB.
- 호스트 공개 포트는 80 뿐 (나머지 서비스는 127.0.0.1). 서비스 로그는 CloudWatch Logs 서비스별 그룹으로 올라가고, 메모리 · 디스크 지표는 CloudWatch Agent 로 올라온다.
- 알람 → SNS → Discord Lambda 경로가 실제 알람 전환으로 동작했다. 웹훅 URL 이 `CHANGE_ME` 이면 Lambda 가 "전송 생략" 로그만 남긴다.
- **첫 배포 중 15~20분 동안 `alb-unhealthy-host` · `ec2-cpu-high` 알람이 울리는 것은 정상이다** (nginx 가 뜨기 전 대상이 비정상, 이미지 빌드로 CPU 급등). 부트스트랩이 끝나면 몇 분 안에 OK 로 돌아온다.
- 이미지를 병렬로 빌드하면 Gradle 캐시 락 경합으로 실패할 수 있어 Dockerfile 의 캐시 마운트를 `sharing=locked` 로 고쳤다 (W-35 브랜치). 이 수정이 들어간 `GitRef` 를 써야 한다.
- **GitHub OIDC 의 `sub` 클레임이 불변 형식**(`repo:owner@소유자ID/repo@저장소ID:ref:...`)이라 처음 템플릿의 신뢰 정책(기존 형식만)이 `AssumeRoleWithWebIdentity` 를 거부했다. CloudTrail 의 `AssumeRoleWithWebIdentity` 실패 이벤트에서 실제 `userName`(= sub)을 확인해 두 형식을 모두 허용하도록 고쳤다. 다른 저장소로 옮기면 `GithubRepo` 만 바꾸면 된다.
- **ECR 경로(`DeployMode=ecr`)도 같은 방식으로 검증**: 태그 푸시 → GitHub OIDC 로 역할 위임 → 서비스 7 + frontend 이미지 8개 ECR push → EC2 가 ECR 에서 pull 해 기동 → ALB smoke 9항목 통과.
- **ECR 수명주기 정책**(8개 저장소 공통): 태그 없는 이미지는 1일 뒤 삭제, 태그가 있는 이미지는 최근 5개만 유지. `get-lifecycle-policy` 로 적용을 확인했다.
- **이미지 경량화**: 런타임을 Alpine JRE 로 바꿔 ECR 압축 크기가 서비스당 148~205MB → 108~165MB(약 22% 감소), EC2 비압축 크기는 347~412MB → 235~300MB. Alpine 에는 curl 이 없어 컨테이너 헬스체크는 busybox wget(127.0.0.1)을 쓴다. 이미지는 push 시 `provenance: false` 로 태그 없는 attestation 항목을 만들지 않는다.
- 저장소 변수 `AWS_ROLE_ARN` 은 저장소 admin 만 만들 수 있다. 시험은 admin 이 아닌 계정이라 임시 커밋에 역할 ARN 을 직접 넣어 태그로 실행했다(태그 · 커밋은 시험 뒤 삭제).
- stack2 의 `ImageTag` · `GitRef` 에는 문자 제한(AllowedPattern)이 있다. CLI 에서 셸 변수를 잘못 넘겨 `$sha` 가 그대로 들어가 user-data 가 깨진 적이 있기 때문이다.
- Windows 의 AWS CLI 로 한글이 들어간 템플릿을 올릴 때는 `AWS_CLI_FILE_ENCODING=UTF-8` 환경변수가 필요하다 (콘솔 업로드는 해당 없음).

## 실패했을 때

| 증상 | 원인 / 조치 |
|---|---|
| stack1 `OIDCProvider already exists` | 계정에 이미 공급자가 있다. `CreateGithubOidcProvider=false` + `ExistingOidcProviderArn` |
| stack1 RDS 엔진 버전 오류 | `DbEngineVersion` / `DbParameterFamily` 를 `10.11` / `mariadb10.11` 로 |
| stack2 `No export named ...` | `Stack1Name` 오타이거나 stack1 이 아직 `CREATE_COMPLETE` 가 아니다 |
| 스택은 성공했는데 ALB 가 503 / 대상 unhealthy | 부트스트랩이 아직 도는 중이다. `/var/log/sporty-userdata.log` 확인. nginx(`/healthz`)가 뜨기 전까지 정상 |
| 앱은 뜨는데 `/api/*` 가 404 | 게이트웨이가 Config 서버에서 라우트를 못 받은 상태. `deploy.sh status` 로 config-service 확인 후 `deploy.sh` 재실행 |
| 컨테이너가 로그 드라이버 오류로 안 뜬다 | awslogs 문제일 때 `/etc/sporty/aws.env` 에 `USE_AWSLOGS=false` 를 넣고 `deploy.sh` 재실행 (로컬 로그만 사용, CloudWatch 로그 · ERROR 알람은 동작하지 않음) |
| 인증서(HTTPS)를 쓰는데 브라우저 POST 가 403 | 게이트웨이 CORS 오리진이 `https://도메인` 과 달라서다. `/etc/sporty/aws.env` 에 `CORS_ALLOWED_ORIGIN=https://도메인` 을 넣고 `deploy.sh` 재실행 |
| 부트스트랩이 `git clone` 에서 실패 | `GitRef` 브랜치/태그가 원격에 없다. 스택 업데이트로 `GitRef` 수정 후 인스턴스 교체 또는 Session Manager 에서 수동 실행 |
| 알람 이메일이 안 온다 | 구독 확인 메일을 아직 누르지 않았다 (스팸함 확인) |

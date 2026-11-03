# AWS 배포 계획 (W-29 계획 · W-35 배포)

담당: 반정욱. 근거: *Sporty MSA 전환 기획안* 의 "AWS 배포 설계" 절. 값이 팀 시트와 다르면 시트를 따른다.

배포는 두 단계로 나눈다.

- **1단계 (필수, 11/4 코드 동결 전)**: EC2 1대에 전체 Compose 를 올리고 DB 만 RDS 3대로 뺀다. 선택 요구사항 OPT-04.
- **2단계 (여유 있을 때만)**: 같은 VPC · 같은 RDS 위에서 서비스만 EKS 로 옮긴다. 시트 선택 목표에 없는 OPT-03(K8s) 가산점이라, **1단계가 시연 가능한 상태가 된 뒤에만** 진행한다.

| 항목 | 1단계: EC2 + RDS | 2단계: EKS |
|---|---|---|
| 요구사항 | OPT-04 클라우드 실배포 | OPT-04 + OPT-03 K8s (시트에 추가 필요) |
| 앱 실행 | EC2 t3.xlarge 1대(메모리 16GB)에서 Docker Compose. 컨테이너: 업무 4 + 공통 3 + kafka · redis · zipkin | EKS 관리형 노드그룹 2대, 서비스마다 Deployment + Service |
| DB | RDS MariaDB 3대 (user_db · match_db · facility_db) | 1단계 RDS 그대로 재사용 |
| Kafka · Redis | Compose 컨테이너 (로컬과 같은 설정) | StatefulSet · Pod (MSK · ElastiCache 는 비용 · 생성 시간 때문에 제외) |
| 진입점 | ALB :443 → EC2 :8000 | AWS Load Balancer Controller Ingress |
| 비밀값 | Secrets Manager → EC2 `.env` (또는 Config `{cipher}`) | Secrets Manager → K8s Secret |
| 준비 시간 (예상) | 2 ~ 3시간 | 추가 3 ~ 4시간 (클러스터 생성 대기 포함) |
| 리스크 | 단일 장애점, 메모리 부족 시 OOM | 시간 초과, 노드 메모리, 비용 |

## 1단계: EC2 1대 + RDS 3대

사용자는 ALB 로만 들어오고, EC2 와 RDS 는 Private Subnet 에 둔다. 외부 API(OpenAI, 서울 열린데이터)는 NAT Gateway 로만 나간다. 프론트 빌드는 같은 Compose 의 nginx 컨테이너가 서빙하고, ALB 가 `/` 는 nginx, `/api/**` 는 gateway 로 보낸다.

### 네트워크 (CIDR 은 안)

| 구역 | CIDR | 들어가는 것 | 보안 그룹 인바운드 |
|---|---|---|---|
| Public Subnet ×2 | 10.0.0.0/24, 10.0.1.0/24 | ALB, NAT Gateway (비용상 1개) | sg-alb: 80 · 443 ← 인터넷 |
| Private App Subnet ×2 | 10.0.10.0/24, 10.0.11.0/24 | EC2 1대 (2단계에서 EKS 노드) | sg-app: 8000 · 80 ← sg-alb. 22 는 열지 않고 SSM 세션 매니저 사용 |
| Private Data Subnet ×2 | 10.0.20.0/24, 10.0.21.0/24 | RDS 3대 (DB Subnet Group) | sg-db: 3306 ← sg-app 만 |

### RDS

- 3대: `db.t4g.micro`, 단일 AZ, 자동 백업 1일.
- 인스턴스마다 서비스 계정 1개만 만든다 (로컬 `compose.yaml` 과 같은 이름: `user_svc` · `match_svc` · `facility_svc`, DB 는 `user_db` · `match_db` · `facility_db`). 다른 서비스 DB 에는 접속 자체가 안 되어야 한다 (MSA-08 증빙).
- 로컬 → AWS 전환이 **접속 주소 교체만**으로 끝나도록 로컬 DB 도 서비스당 1대로 맞춰 두었다.

### Compose 구성

EC2 에서는 `docker compose -f compose.yaml -f compose.app.yaml -f compose.aws.yaml up -d` 로 올린다. `compose.app.yaml` 은 앱 서비스 7개 + 프론트(nginx)를 올리는 override(W-34 선행), `compose.aws.yaml` 은 로컬 MariaDB 컨테이너 3개를 빼고 DB 주소만 RDS 엔드포인트로 바꾸는 override 이다 (W-35 에서 작성).

- 서비스 이미지: 서비스마다 `backend/{서비스}/Dockerfile` (담당자가 원본 관리), 이를 이어 붙인 master `backend/Dockerfile` 의 `--target {서비스}` 로 빌드해 ECR 에 push. 프론트는 `frontend/Dockerfile`(nginx). Dockerfile 구조는 CONTRIBUTING 의 "서비스 이미지 빌드" 를 따른다.
- 기동 순서는 `depends_on` + healthcheck 로 건다: config → discovery → 업무 서비스 → gateway.
- 메모리 16GB 안에 업무 4 + 공통 3 + kafka · redis · zipkin 이 들어가는지가 1단계의 주된 리스크. OOM 이 나면 서비스별 `JAVA_TOOL_OPTIONS` 의 `MaxRAMPercentage` 를 낮추거나 zipkin 을 먼저 뺀다.

### 절차 (11/4)

| 순서 | 할 일 |
|---|---|
| 0 (11/3, W-29) | AWS 계정 수령, 리전 `ap-northeast-2`, 비용 한도 알림(Budgets), 이 문서 머지 |
| 1 | VPC · 서브넷 6개 · NAT · 보안 그룹 3개 생성 |
| 2 | **RDS 3대 생성** (10분 내외 걸려서 가장 먼저 시작), 스키마 · 계정 생성 |
| 3 | ECR 저장소 8개 (서비스 7 + frontend), GitHub Actions 가 OIDC 역할로 빌드 후 push |
| 4 | EC2 생성 (Amazon Linux 2023, Docker · compose 플러그인), IAM 역할에 ECR pull · Secrets Manager 읽기 · SSM |
| 5 | Secrets Manager 값으로 `.env` 생성 → `docker compose up -d` |
| 6 | ALB 대상 그룹 헬스체크 `/actuator/health`, 프론트 `REACT_APP_BACKEND_ENDPOINT` 를 ALB 주소로 빌드 |
| 7 | 배포 주소로 시연 흐름(W-27) 확인 → W-41 코드 동결 태그 |

### 비밀값

`.env` 와 키는 저장소에 두지 않는다. Secrets Manager 에 저장하고 EC2 에서 `.env` 로 내려받는다 (또는 Config 서버의 `{cipher}`, OPT-02). 필요한 값: DB 계정 3개 비밀번호, `JWT_SECRET_KEY`(gateway 와 user-service 가 같은 값), `OPEN_AI_KEY`, `PUBLIC_DATA_API_KEY`.

## 2단계: EKS (같은 VPC · 같은 RDS)

1단계에서 메모리를 나눠 쓰던 컨테이너를 Pod 로 옮기고, RDS · 보안 그룹 · ECR 은 그대로 둔다. Eureka 는 Pod IP 로 등록(`eureka.instance.prefer-ip-address: true`)하고, 서비스 간 주소는 1단계와 같이 Eureka 이름으로 부른다.

1. `eksctl` 로 기존 VPC 의 Private App Subnet 에 클러스터 + 관리형 노드그룹 2대 생성.
2. AWS Load Balancer Controller 설치(helm), Ingress 로 `/api/**` → gateway Service.
3. 서비스 7개 Deployment · Service · ConfigMap, kafka StatefulSet + PVC, redis Pod.
4. readiness probe `/actuator/health`, Secrets Manager → K8s Secret.
5. 같은 시연 흐름으로 확인한 뒤 결과보고서 배포 아키텍처도(W-39)에 두 단계를 모두 넣는다.

## 정리 (비용)

발표(11/5 13:00)가 끝나면 EKS · NAT Gateway · RDS 3대 · ALB 를 바로 삭제한다. NAT 와 EKS 컨트롤 플레인은 켜 둔 시간만큼 과금된다.

## 열린 질문

- 강사님 답(W-15 질문 목록의 AWS 범위)에 따라 계정 나눔 · 비용 한도가 바뀔 수 있다.
- DB 3대 안은 팀 시트(1대 · 스키마 3개)와 달라 시트 수정 합의가 필요하다 (서비스 목록 탭 mariadb 행, 가이드 4장 기본값 6번, W-06 완료 기준).
- 2단계 EKS 를 할지, 한다면 OPT-03 을 시트 선택 목표에 추가할지.

# Kubernetes 로컬 배포 (선택 요구사항 3 · W-50)

Docker Desktop 내장 Kubernetes에 Sporty 전체(앱 7 · 프론트 · DB 3 · Redis · Kafka · Zipkin)를 올린다. EKS 배포는 하지 않는다.

## 구성
| 파일 | 내용 |
|---|---|
| `00-namespace.yaml` | 네임스페이스 `sporty` |
| `10-infra.yaml` | MariaDB 3대(user · match · facility) · Redis · Kafka(KRaft 1노드) · Zipkin + 토픽 생성 Job |
| `20-config.yaml` | 앱 공통 ConfigMap(Eureka · Config · Kafka · Zipkin 주소) · 프론트 nginx 설정 |
| `30-platform.yaml` | Eureka(discovery) · Config 서버 |
| `40-services.yaml` | user · match · facility · ai |
| `50-edge.yaml` | Gateway · 프론트 nginx (LoadBalancer) |
| `up.sh` · `verify.sh` · `down.sh` | 배포 · 확인 · 삭제 |

- 이미지 = `docker compose -f compose.yaml -f compose.app.yaml build`로 만든 `sporty/*:local`. `imagePullPolicy: Never`라 레지스트리를 쓰지 않는다. **develop을 새로 받았으면 빌드부터 다시 한다**(`--no-build`는 이미지가 지금 코드와 같을 때만)
- 비밀값은 저장소에 두지 않는다. `up.sh`가 루트 `.env`에서 읽어 Secret `sporty-secrets`를 만든다
- 데이터는 emptyDir(파드를 지우면 사라짐)

### compose와 다른 점
서비스 이름 · 포트 · 환경변수 값은 `compose.yaml` · `compose.app.yaml`과 같다. 다른 곳만 적는다.

| 항목 | compose | Kubernetes | 이유 |
|---|---|---|---|
| Kafka 리스너 | `kafka:29092` + 바깥 `localhost:9092` | `kafka:29092`만 | 바깥 리스너가 `localhost:9092`를 알려 주면 파드는 자기 자신에 붙는다. IDE에서 붙을 일이 없어 뺐다 |
| Kafka 준비 확인 | `kafka-topics.sh --list` (10초 · 20회) | 29092 포트 열림(`tcpSocket`) | `kafka-topics.sh`는 실행마다 JVM을 띄워 10초를 넘겼다(실측 31회 연속 시간 초과). 토픽 생성 확인은 `kafka-init` Job이 한다 |
| Zipkin 주소 | `ZIPKIN_ENDPOINT` 환경변수(#62) | ConfigMap `ZIPKIN_ENDPOINT` (discovery 포함 7개, 같은 값) | 컨테이너 안 `localhost`는 자기 자신 |
| 기동 순서 | `depends_on` + healthcheck | initContainer `wait-deps`가 앞 서버 응답 · 포트를 기다림 | Kubernetes에는 기동 순서 지정이 없다 |
| 서울시 키 · 주소 | 빈 값도 넘김 | 키가 있을 때만 Secret에 넣고, 주소가 비면 서울시 기본 주소 | 빈 값을 넘기면 코드의 기본 주소를 덮어 동기화가 실패한다 |
| 프론트 nginx 설정 | 이미지 안 `nginx.conf`(Docker DNS 127.0.0.11) | ConfigMap `frontend-nginx`로 덮음 | 클러스터에는 127.0.0.11이 없다 |
| 프론트 · 게이트웨이 주소 | `localhost:80` · `:8000` | `localhost:8080` · `:8000` (LoadBalancer) | CORS 허용 주소도 `http://localhost:8080` |
| DB 데이터 | 볼륨(남음) | emptyDir(파드를 지우면 사라짐) | 시연 · 검증용 |
| 재시작 방식(회원 · 매치 · 시설) | 컨테이너를 내리고 다시 띄움 | `strategy: Recreate`(옛 파드를 먼저 내림). 게이트웨이 · AI · 프론트는 기본(RollingUpdate) | 기본 방식은 새 파드를 먼저 띄워 잠깐 두 대가 된다. 실측: outbox 발행기 둘이 같은 행을 고쳐 `Record has changed since last read` 오류, 시설은 `ddl-auto: create-drop`이라 내려가는 옛 파드가 새 파드가 만든 테이블을 지움(`facility_outbox doesn't exist`). 대신 재시작 동안 그 서비스는 잠깐 멈춘다 |

### 서울시 시설 데이터 (매치를 만들려면 필요)
루트 `.env`에 아래 한 줄을 넣고 `bash k8s/up.sh --no-build`를 다시 실행한다. 주소(`PUBLIC_DATA_API_URL`)는 비워도 된다. `verify.sh` 끝의 INFO 줄로 시설이 들어왔는지 본다.
```
PUBLIC_DATA_API_KEY=<서울시 열린데이터 키>
```

## 실행 (Windows · Git Bash)
1. Docker Desktop › Settings › Kubernetes › Enable Kubernetes 켜기(클러스터 종류는 kubeadm). 켜지면 `kubectl config use-context docker-desktop`
2. compose로 띄운 스택이 있으면 내린다(포트 · 메모리 겹침): `docker compose -f compose.yaml -f compose.app.yaml down`
3. 저장소 루트에서 `cp .env.example .env`(이미 있으면 생략)
4. `bash k8s/up.sh` (이미지가 이미 있으면 `--no-build`)
5. `bash k8s/verify.sh` → 전부 PASS
6. 브라우저 `http://localhost:8080` 에서 회원가입 → 로그인 → 매치 목록

| 주소 | 무엇 |
|---|---|
| http://localhost:8080 | 프론트 (nginx가 `/api/`를 게이트웨이로) |
| http://localhost:8000 | 게이트웨이 |
| http://localhost:8761 | Eureka 대시보드 |
| http://localhost:9411 | Zipkin |

## 자주 막히는 곳
| 증상 | 원인 · 해결 |
|---|---|
| `ErrImageNeverPull` | 이미지가 클러스터에 안 보임. 빌드를 먼저 하고, Docker Desktop Kubernetes 종류가 kind면 kubeadm으로 바꾼다 |
| 파드가 `Init:0/1`에서 안 넘어감 | 앞 서버 대기 중. `kubectl -n sporty logs <파드> -c wait-deps`로 무엇을 기다리는지 본다 |
| `OOMKilled` · 노드 Pending | Docker Desktop 메모리가 부족함. Settings › Resources에서 늘리거나 다른 앱을 닫는다(전체 한도 합계 약 7.5GB) |
| 게이트웨이 503 | 기동 직후 30 ~ 60초는 Eureka 캐시 때문에 정상. 기다린 뒤 다시(`verify.sh`는 90초까지 다시 본다) |
| kafka 파드가 `0/1 Running`에서 안 넘어감 | 준비 확인이 실패하는 중. `kubectl -n sporty describe pod -l app=kafka`의 Events를 본다 |
| 게이트웨이가 안 뜸(jwt.secret) | `.env`에 `ENCRYPT_KEY`가 없음 |

## 시연 스위치
- 탈퇴 Saga 실패: `kubectl -n sporty set env deploy/match-service SAGA_FAIL_CLEANUP=true` (끄기 = `false`). 값을 바꾸면 파드가 새로 뜬다(1 ~ 2분)
- 서비스 중지: `kubectl -n sporty scale deploy/facility-service --replicas=0` (켜기 = `--replicas=1`)
- 자동 복구: `kubectl -n sporty delete pod -l app=user-service` → 같은 Deployment가 새 파드를 띄운다(`kubectl -n sporty get pods -w`로 확인)
- 매치 서비스는 2대로 늘리지 않는다. outbox 발행기가 서비스 안에 있어 2대면 같은 이벤트를 두 번 보낸다

## 지우기
`bash k8s/down.sh` (네임스페이스째 삭제, 이미지는 남음)

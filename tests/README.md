# 테스트 (MSA 관점)

서비스를 나눈 뒤에도 "시트 TC 가 실제로 동작하는지", "한 서비스가 멈춰도 다른 기능이 살아 있는지", "부하에서 어디가 먼저 깨지는지" 를 자동으로 보는 두 가지 파이프라인이다.

| 구분 | 위치 | 이슈 | 실행 |
|---|---|---|---|
| 테스트 케이스 파이프라인 (점수제) | `tests/pipeline/` | #43 | `.github/workflows/msa-tests.yml` |
| 부하 테스트 (k6) | `tests/load/` | #44 | `.github/workflows/load-test.yml` (수동) |

**이 파이프라인은 결과를 보여 주는 용도다.** 테스트로 드러난 서비스 결함은 해당 서비스 담당자가 고친다 (테스트 코드를 맞추려고 서비스 응답을 바꾸지 않는다). 테스트 자체가 잘못된 것 같으면 이슈로 알린다.

## 1. 테스트 케이스 파이프라인 (스위트 8개 · 테스트 29개 · 100점)

| 스위트 | 서비스 | 점수 | 시트 TC · 요구사항 |
|---|---|---|---|
| A. 게이트웨이 · 보안 | gateway | 15 | MSA-03 |
| B. 회원 | user | 20 | TC-01 ~ 08 |
| C. 매치 | match | 20 | TC-09 ~ 16 |
| D. 시설 | facility | 10 | TC-20 · 21 |
| E. AI | ai | 10 | TC-17 ~ 19 (OpenAI 키 없이 계약 테스트) |
| F. 장애 격리 | gateway · match · facility · user | 10 | TC-22 · 23 (MSA-10 · OPT-05) |
| G. 이벤트 (Kafka) | facility · match | 10 | TC-24 |
| H. 탈퇴 Saga | user · match | 5 | TC-04 · 25 (OPT-10) |

- 테스트마다 점수가 있고 PASS 면 획득, FAIL 은 0점, SKIP(컨테이너를 제어할 수 없는 환경)은 만점 계산에서 제외한다.
- 아직 구현되지 않은 기능(W-18 · W-22 · W-32 등)의 테스트는 FAIL 에 "대기: W-xx" 로 표시된다. 해당 PR 이 머지되면 점수가 오른다.
- 테스트 이름에 시트 TC 번호를 달았으므로, 시트의 "미수행" 을 이 결과로 갱신하면 된다.

### 로컬 실행

```bash
cp .env.example .env
docker compose -f compose.yaml -f compose.app.yaml up -d --build      # 첫 기동은 kafka-init 때문에 exit 1 이어도 괜찮다 (ps 로 healthy 확인)
bash tests/pipeline/fixtures.sh                                       # 시설 · 시설 스냅샷 시드 (여러 번 실행해도 안전)
bash tests/pipeline/run.sh                                            # 전체
bash tests/pipeline/run.sh --suite A,B,C,D                            # 컨테이너를 멈추지 않는 스위트만
MIN_SCORE=60 bash tests/pipeline/run.sh                               # 60점 미만이면 종료 코드 1
BASE=http://<ALB> NO_DOCKER=1 bash tests/pipeline/run.sh --suite A,B,C,D,E   # 배포 주소 점검 (컨테이너 제어 불가)
```

- 기동 직후 30~60초는 Eureka 반영 때문에 게이트웨이가 503 을 줄 수 있다. 기동이 끝난 뒤 실행한다.
- F · E3 · G 는 컨테이너를 **실제로 멈췄다 올린다** (끝나면 복구를 기다린다). 로컬에서 다른 작업 중이면 `--suite` 로 제외한다.
- 결과: `tests/pipeline/results/{results.tsv, results.json, summary.md}`.

### 테스트 추가 방법

1. `tests/pipeline/suites/<스위트>.sh` 에 함수를 만들고 맨 아래에 `tc <점수> "<TC/요구사항>" "<이름>" <함수> [대기 WBS]` 를 추가한다.
2. 함수 안에서 `new_user` · `create_match` · `req` · `expect_code` · `expect_eq` 같은 `tests/pipeline/lib.sh` 의 도우미를 쓴다 (JSON 값은 `jget .경로`).
3. 한 스위트의 점수 합계는 이슈 #43 의 표와 맞춘다 (총 100점).

### GitHub Actions

`msa-tests.yml` — **배포 전에 돌려야 하는 것만** CI/CD 에 둔다: main 으로 가는 PR · 태그 `v*` push(`image.yml` 이 먼저 호출, 통과해야 ECR push) · 수동. 전체 스택을 빌드 · 기동해 8개 스위트를 돌리고 이어서 k6 스모크(1 VU 30초)를 건다. **Job Summary 에 서비스별 점수표**, 아티팩트로 `results.json` · 컨테이너 로그를 남긴다. 합격선은 수동 입력 `min_score` 또는 저장소 변수 `MIN_SCORE` (기본 80). 매 PR · 야간 실행은 없다. 스트레스 · 스파이크 · 소크 · 장애 주입 부하는 CI/CD 에 넣지 않고 `load-test.yml` 수동 실행 또는 로컬에서 돌린다.

## 2. 부하 테스트 (k6)

플랜 · SLO · 점수는 이슈 #44. 시나리오는 `tests/load/k6/scenarios.js` 하나에서 `SCENARIO` 로 고른다.

```bash
k6 run tests/load/k6/scenarios.js -e SCENARIO=smoke                       # 1 VU 30초, 전 엔드포인트 1회
k6 run tests/load/k6/scenarios.js -e SCENARIO=average -e VUS=20 -e DURATION=5m
k6 run tests/load/k6/scenarios.js -e SCENARIO=stress                       # 20 → 200 VU 단계 상승
k6 run tests/load/k6/scenarios.js -e SCENARIO=spike
k6 run tests/load/k6/scenarios.js -e SCENARIO=soak -e VUS=30 -e DURATION=30m
bash tests/load/chaos.sh                                                  # 평균 부하 중 facility-service 중지 → 재기동
python tests/load/score.py                                                # 결과 JSON 으로 점수 계산 (측정한 항목만)
```

- 대상은 `-e BASE_URL=...` 로 바꾼다 (게이트웨이 8000, 1차 모놀리스 8080, AWS ALB). 같은 스크립트로 1차 · 2차를 비교한다 (W-37).
- 실행 전에 시설 시드를 넣는다: `bash tests/pipeline/fixtures.sh`.
- 지표는 `svc`(gateway · match · user · facility · ai) · `kind`(read · write) 태그로 나뉜다.
- **부하 발생기 자원이 먼저 한계일 수 있다.** 스트레스는 전용 환경(본인 PC 또는 별도 인스턴스)에서 하고, EC2 앱 서버 안에서 돌리지 않는다.

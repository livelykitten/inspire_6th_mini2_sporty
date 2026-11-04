# AI 서비스 테스트 케이스

실행 방법은 [README](README.md)를 참고합니다. 이 문서는 검증 계획입니다. 실행하지 않은 항목을 통과로 기록하지 않습니다.

## 공통 준비

- Config Server·Eureka·AI 서비스를 실행합니다.
- AI-02에는 실제 match-service, AI-03에는 실제 user-service·match-service와 테스트 데이터가 필요합니다.
- 직접 호출 주소: `http://localhost:8004`. 보호 API에는 `X-User-Id: <실제 회원 ID>`를 전달합니다.
- Gateway 호출 주소: `http://localhost:8000`. 보호 API에는 로그인으로 발급받은 `Authorization: Bearer <access token>`을 전달합니다.
- POST는 Postman의 `Body → raw → JSON`을 선택하고 `Content-Type: application/json`을 지정합니다.
- 날짜 기대값은 테스트 실행일의 Asia/Seoul 기준으로 계산합니다. 이전 Mock의 고정 ID를 실제 연동 기대값으로 사용하지 않습니다.

## AI-01: 개설 초안

`POST /api/ai/matches`

| ID | 입력/상황 | 기대 결과 |
|---|---|---|
| D-01 | `{"prompt":"이번 토요일 저녁 7시 강남에서 초보 풋살 10명 모집"}` | 200, initialValues에 FUTSAL·강남구·BEGINNER·인원 10·해당 토요일 19시 반영 |
| D-02 | `{"prompt":"강남에서 농구 매치 만들어줘"}` | 200, BASKETBALL·강남구 반영. 나머지는 기존 기본값 정책에 따라 확인 |
| D-03 | `{"prompt":"매치 만들어줘"}` | 400, code null, 매치 정보를 찾지 못했다는 지정 안내 |
| D-04 | `{"prompt":"강남에서 스쿼시 4명 모집"}` | 400, 미지원 조건 및 지원 종목 안내 |
| D-05 | prompt가 빈 문자열 또는 공백 | 400, `요청 내용을 입력해주세요.` |
| D-06 | prompt 501자 | 400, `요청은 500자 이하로 입력해주세요.` |
| D-07 | 인증 정보 없이 요청 | 401 |

제목·설명의 생성 문구는 고정하지 않습니다. 초안 생성으로 실제 매치가 저장되지 않는지도 확인합니다.

## AI-02: 자연어 검색

`POST /api/ai/matches/search`

| ID | 입력/상황 | 기대 결과 |
|---|---|---|
| S-01 | `{"prompt":"이번 주말 강남에서 초보 풋살 찾아줘"}` | 200, conditions·matches·criteria 존재. criteria에 FUTSAL·강남구·BEGINNER·주말 날짜 반영 |
| S-02 | `{"prompt":"잠실에서 농구 찾아줘"}` | BASKETBALL·송파구 반영 |
| S-03 | `{"prompt":"내일 풋살 찾아줘"}` | 서울 시간 기준 내일의 검색 범위 |
| S-04 | `{"prompt":"여자 초급 농구 모집 중인 매치 찾아줘"}` | FEMALE·BEGINNER·BASKETBALL·RECRUITING 반영 |
| S-05 | `{"prompt":"풋살 찾아줘"}` | FUTSAL 반영. 미지정 지역·성별 등을 임의로 채우지 않음 |
| S-06 | `{"prompt":"안녕하세요"}` | 400, code null, 검색 조건을 찾지 못했다는 지정 안내 |
| S-07 | `{"prompt":"스쿼시 매치 찾아줘"}` | 400, 미지원 조건 및 지원 종목 안내 |
| S-08 | 공백 prompt / 501자 prompt | 입력 검증 400 |
| S-09 | 조건에 맞는 실제 매치가 없는 데이터 준비 | 200, matches 빈 배열, criteria 유지 |
| S-10 | 인증 정보 없이 유효한 검색 요청 | 공개 API로 처리. 의존 서비스·OpenAI 정상 시 200 |

검색 필터링은 실제 match-service의 결과와 비교합니다. 내 매치 제외는 추천에만 적용하므로 검색에는 내 매치가 포함될 수 있습니다. conditions는 현재 추출된 항목만 반환합니다.

## AI-03: 개인화 추천

`GET /api/ai/matches/recommendations`, Body 없음.

테스트 회원의 성별·활동 자치구·선호 종목을 기록하고, 모집 중·시작 전인 다음 데이터를 준비합니다.

| 데이터 | 조건 | 기대 |
|---|---|---|
| A | 내가 이미 참가 | 제외 |
| B | 내가 직접 개설 | 제외 |
| C | 선호 종목·활동 지역 일치, 성별 조건 충족 | 후보 |
| D | 선호 종목만 일치, 성별 조건 충족 | 후보 |
| E | 활동 지역만 일치, 성별 조건 충족 | 후보 |
| F | 종목·지역 일치하나 성별 불일치 | 제외 |
| G | 종목·지역 모두 불일치 | 제외 |

| ID | 상황 | 기대 결과 |
|---|---|---|
| R-01 | 위 데이터로 추천 | 200, C·D·E 반환. 순서는 고정하지 않음 |
| R-02 | 응답 ID 확인 | A·B·F·G 미포함, 중복 없이 최대 3개 |
| R-03 | 후보를 2개만 남기도록 구성 | 200, 후보 2개 반환 |
| R-04 | 필터링 후 후보가 없도록 구성 | 200, 빈 배열, OpenAI 추천 호출 없음 |
| R-05 | 선호 종목 없음·지역 있음 | 지역과 성별로 후보 구성 |
| R-06 | 지역 없음·선호 종목 있음 | 종목과 성별로 후보 구성 |
| R-07 | OpenAI 호출 실패를 자동 테스트에서 주입 | 500, code null, `추천 매치를 만들지 못했습니다. 잠시 후 다시 시도해주세요.` |

R-04의 AI 미호출은 응답만으로 증명하지 않고 자동 테스트/호출 기록으로 확인합니다. IN-04가 OWNER·PARTICIPANT 매치를 모두 반환하는지도 확인합니다.

## 인증·Gateway·CORS·헬스체크

| ID | 요청/상황 | 기대 결과 |
|---|---|---|
| A-01 | 8004 추천 요청에 양의 정수 X-User-Id | 인증 통과. 회원 존재 여부는 별도 |
| A-02 | 8004 추천 요청에 헤더 없음 | 401 |
| A-03 | 헤더 abc·0·-1·Long 범위 초과 | 401 |
| G-01 | 8000 AI-01에 유효한 JWT | 8004 Controller 도달, 정상 AI 환경에서 200 |
| G-02 | 8000 AI-01/03에 토큰 없음·만료·잘못된 서명 | 401 |
| G-03 | 8000 보호 요청에 JWT 없이 X-User-Id만 지정 | 401, 외부 헤더로 인증 우회 불가 |
| G-04 | 유효 JWT와 다른 사용자의 X-User-Id를 함께 전달 | Gateway가 JWT 사용자 ID로 덮어씀. 로그/자동 테스트로 확인 |
| G-05 | 8000 검색 요청에 Origin: http://localhost:3000 | Access-Control-Allow-Origin이 정확한 origin 한 번만 존재 |
| G-06 | 같은 origin의 OPTIONS 사전 요청 | 200 또는 204, CORS 허용 헤더와 중복 없음 확인 |
| H-01 | 인증 없이 GET http://localhost:8004/actuator/health | 정상 상태에서 200, status UP |
| H-02 | 기동 로그와 Eureka 대시보드 | Started AiServiceApplication 및 AI-SERVICE UP 확인 |

Postman은 CORS를 강제하지 않으므로 G-05/06은 응답 Headers를 확인합니다. 실제 브라우저도 별도로 확인합니다. Eureka의 오래된 UP 등록만으로 기동 성공을 판단하지 않습니다. ALB·Compose healthcheck는 인프라 담당과 확인합니다.

## 의존 서비스 장애·타임아웃

팀 공유 서버를 임의로 중지하지 않습니다. 중지·지연·오류 응답 주입은 로컬 전용 인스턴스나 자동 테스트에서 수행합니다.

| ID | 상황 | 기대 결과 |
|---|---|---|
| E-01 | 로컬 user-service 중지 후 추천 | 503, AI_DEPENDENCY_UNAVAILABLE |
| E-02 | 로컬 match-service 중지 후 검색/추천 | 503, AI_DEPENDENCY_UNAVAILABLE. 검색은 AI 조건 추출 성공이 전제 |
| E-03 | 연결 실패·읽기 타임아웃 주입 | 503, AI_DEPENDENCY_UNAVAILABLE |
| E-04 | IN-03/04/05의 503 응답 주입 | 503, AI_DEPENDENCY_UNAVAILABLE |
| E-05 | IN-03의 검색 조건 400 주입 | 400 유지 |
| E-06 | IN-05의 회원 조회 404 주입 | 404, USER_NOT_FOUND |
| E-07 | 다른 Feign 오류 주입 | 일반 500, AI_DEPENDENCY_UNAVAILABLE 코드 미사용 |
| E-08 | OpenAI 지연 응답을 자동 테스트에서 주입 | HTTP 타임아웃 적용. 추천 호출 실패는 지정된 500 처리 |

E-01/02는 Eureka 캐시 영향을 받을 수 있습니다. 연결 거절은 타임아웃보다 먼저 발생할 수 있으므로 정확히 2초/3초인지로 통과 여부를 판단하지 않습니다. 전체 추천 시간에는 여러 Feign 호출이 포함됩니다.

503 응답:

```json
{
  "code": "AI_DEPENDENCY_UNAVAILABLE",
  "message": "잠시 후 다시 시도해주세요."
}
```

## 검증 기록

| 실행일 | 케이스 ID | 커밋/환경 | 결과(통과/실패/미실행) | 증빙·이슈 |
|---|---|---|---|---|
| | | | | |

요청 URL·상태 코드·응답을 증빙으로 남깁니다. JWT·OpenAI 키·암호화 키는 가립니다. 자동 테스트와 실제 서비스 통합 테스트 결과를 구분하여 기록합니다.

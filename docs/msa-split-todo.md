# 서비스 경계 import 목록 (구조 배치 직후 상태)

1차 `backend/sporty` 코드를 서비스 디렉터리에 **복사만** 했고 로직은 수정하지 않았다. 아래는 서비스 경계를 넘어 남아 있는 `import` 이다.
`commons` 는 서비스마다 통째로 복사했으므로(공통 모듈 없음) 경계가 아니다. 이 목록이 비면 그 서비스가 단독으로 컴파일된다.

| 서비스 | `compileJava` | 경계 import |
|---|---|---|
| user-service | 실패 | 2곳 |
| match-service | 실패 | 49곳 |
| facility-service | 성공 | 0곳 |
| ai-service | 실패 | 40곳 |

파일 단위 import 줄 수이며 main 과 test 를 합친 값이다. 서비스 소스 전체에서 `grep`/IDE 로 다시 뽑을 수 있다.

## user-service (W-07)

대응 결합 지점: 결합 지점 4. `withdrawal()` 안의 `matchService` 호출 → 탈퇴 Saga EV-02/03/04. W-22, W-32

### `exerciseMatching` (match-service) 참조 — 2곳

- `main/java/com/example/sporty/features/users/service/UserService.java`
  - L7: `import com.example.sporty.features.exerciseMatching.service.MatchService;`
- `test/java/com/example/sporty/features/users/service/UserWithdrawalTest.java`
  - L27: `import com.example.sporty.features.exerciseMatching.service.MatchService;`

## match-service (W-08)

대응 결합 지점: 결합 지점 1·2·3 (시설 FK → `service_id`, 시설 스냅샷, `user_id` + Feign IN-01/02). R3 연쇄 수정 범위 포함. W-08, W-16/17, W-19

### `facilities` (facility-service) 참조 — 15곳

- `main/java/com/example/sporty/features/exerciseMatching/domain/dto/MatchCreateRequestDto.java`
  - L9: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `main/java/com/example/sporty/features/exerciseMatching/domain/entity/MatchEntity.java`
  - L30: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `main/java/com/example/sporty/features/exerciseMatching/service/MatchService.java`
  - L29: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
  - L30: `import com.example.sporty.features.facilities.repository.ServiceRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/MatchFacilityTest.java`
  - L21: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/MatchIntegrationTest.java`
  - L14: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchDeleteControllerTest.java`
  - L45: `import com.example.sporty.features.facilities.repository.ServiceRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchDetailControllerTest.java`
  - L49: `import com.example.sporty.features.facilities.repository.ServiceRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchJoinControllerTest.java`
  - L49: `import com.example.sporty.features.facilities.repository.ServiceRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchLeaveControllerTest.java`
  - L44: `import com.example.sporty.features.facilities.repository.ServiceRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchDeleteIntegrationTest.java`
  - L16: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchJoinIntegrationTest.java`
  - L14: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchLeaveIntegrationTest.java`
  - L14: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchServiceTest.java`
  - L43: `import com.example.sporty.features.facilities.repository.ServiceRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MyMatchesTest.java`
  - L7: `import com.example.sporty.features.facilities.domain.entity.ServiceEntity;`

### `profiles` (user-service) 참조 — 12곳

- `main/java/com/example/sporty/features/exerciseMatching/service/MatchService.java`
  - L52: `import com.example.sporty.features.profiles.domain.entity.ProfileEntity;`
  - L53: `import com.example.sporty.features.profiles.repository.ProfileRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/MatchIntegrationTest.java`
  - L34: `import com.example.sporty.features.profiles.domain.entity.District;`
  - L35: `import com.example.sporty.features.profiles.domain.entity.ProfileEntity;`
  - L36: `import com.example.sporty.features.profiles.repository.ProfileRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchDeleteControllerTest.java`
  - L42: `import com.example.sporty.features.profiles.repository.ProfileRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchDetailControllerTest.java`
  - L45: `import com.example.sporty.features.profiles.domain.entity.ProfileEntity;`
  - L46: `import com.example.sporty.features.profiles.repository.ProfileRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchJoinControllerTest.java`
  - L46: `import com.example.sporty.features.profiles.repository.ProfileRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchLeaveControllerTest.java`
  - L40: `import com.example.sporty.features.profiles.repository.ProfileRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchServiceTest.java`
  - L39: `import com.example.sporty.features.profiles.domain.entity.ProfileEntity;`
  - L40: `import com.example.sporty.features.profiles.repository.ProfileRepository;`

### `users` (user-service) 참조 — 22곳

- `main/java/com/example/sporty/features/exerciseMatching/domain/entity/MatchParticipantEntity.java`
  - L11: `import com.example.sporty.features.users.domain.entity.UserEntity;`
- `main/java/com/example/sporty/features/exerciseMatching/service/MatchService.java`
  - L41: `import com.example.sporty.features.users.domain.entity.UserEntity;`
  - L42: `import com.example.sporty.features.users.domain.entity.UserStatus;`
  - L43: `import com.example.sporty.features.users.repository.UserRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/MatchIntegrationTest.java`
  - L37: `import com.example.sporty.features.users.domain.entity.Gender;`
  - L38: `import com.example.sporty.features.users.domain.entity.UserEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchDeleteControllerTest.java`
  - L43: `import com.example.sporty.features.users.domain.entity.UserEntity;`
  - L44: `import com.example.sporty.features.users.repository.UserRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchDetailControllerTest.java`
  - L47: `import com.example.sporty.features.users.domain.entity.UserEntity;`
  - L48: `import com.example.sporty.features.users.repository.UserRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchJoinControllerTest.java`
  - L47: `import com.example.sporty.features.users.domain.entity.UserEntity;`
  - L48: `import com.example.sporty.features.users.repository.UserRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/controller/MatchLeaveControllerTest.java`
  - L42: `import com.example.sporty.features.users.domain.entity.UserEntity;`
  - L43: `import com.example.sporty.features.users.repository.UserRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchDeleteIntegrationTest.java`
  - L12: `import com.example.sporty.features.users.domain.entity.UserEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchJoinIntegrationTest.java`
  - L10: `import com.example.sporty.features.users.domain.entity.UserEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchLeaveIntegrationTest.java`
  - L10: `import com.example.sporty.features.users.domain.entity.UserEntity;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MatchServiceTest.java`
  - L41: `import com.example.sporty.features.users.domain.entity.UserEntity;`
  - L42: `import com.example.sporty.features.users.repository.UserRepository;`
- `test/java/com/example/sporty/features/exerciseMatching/service/MyMatchesTest.java`
  - L8: `import com.example.sporty.features.users.domain.entity.UserEntity;`
- `test/java/com/example/sporty/support/MatchUserFixtures.java`
  - L8: `import com.example.sporty.features.users.domain.entity.Gender;`
  - L9: `import com.example.sporty.features.users.domain.entity.UserEntity;`

## facility-service (W-09)

대응 결합 지점: 없음

경계 import 없음.

## ai-service (W-10)

대응 결합 지점: 결합 지점 5. 매치·회원 직접 주입 → Feign IN-03/04/05, DTO·enum 은 ai 에 복사 (R10). W-10, W-20

### `exerciseMatching` (match-service) 참조 — 33곳

- `main/java/com/example/sporty/features/ai/agent/MatchAiAgent.java`
  - L15: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;`
  - L16: `import com.example.sporty.features.exerciseMatching.service.MatchService;`
- `main/java/com/example/sporty/features/ai/agent/MatchDraftAiAgent.java`
  - L15: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
- `main/java/com/example/sporty/features/ai/agent/MatchRecommendAiAgent.java`
  - L19: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;`
  - L20: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;`
  - L21: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L22: `import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;`
  - L23: `import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;`
  - L24: `import com.example.sporty.features.exerciseMatching.service.MatchService;`
- `main/java/com/example/sporty/features/ai/controller/AiController.java`
  - L22: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;`
- `main/java/com/example/sporty/features/ai/domain/dto/AiDraftDto.java`
  - L6: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L7: `import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;`
- `main/java/com/example/sporty/features/ai/domain/dto/AiSearchConditionDto.java`
  - L8: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;`
  - L9: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L10: `import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;`
  - L11: `import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;`
- `main/java/com/example/sporty/features/ai/domain/dto/AiSearchResponseDto.java`
  - L5: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;`
- `main/java/com/example/sporty/features/ai/tools/MatchAiTool.java`
  - L12: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L13: `import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;`
  - L14: `import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;`
- `main/java/com/example/sporty/features/ai/tools/MatchDraftAiTool.java`
  - L11: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L12: `import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;`
- `test/java/com/example/sporty/features/ai/agent/AiUnsupportedConditionTest.java`
  - L19: `import com.example.sporty.features.exerciseMatching.service.MatchService;`
- `test/java/com/example/sporty/features/ai/agent/MatchAiAgentTest.java`
  - L24: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L25: `import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;`
  - L26: `import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;`
- `test/java/com/example/sporty/features/ai/agent/MatchRecommendAiAgentTest.java`
  - L27: `import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;`
  - L28: `import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;`
  - L29: `import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;`
  - L30: `import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;`
  - L31: `import com.example.sporty.features.exerciseMatching.service.MatchService;`
- `test/java/com/example/sporty/features/ai/controller/AiControllerTest.java`
  - L30: `import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;`
  - L31: `import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;`

### `profiles` (user-service) 참조 — 1곳

- `test/java/com/example/sporty/features/ai/agent/MatchRecommendAiAgentTest.java`
  - L32: `import com.example.sporty.features.profiles.domain.entity.District;`

### `users` (user-service) 참조 — 6곳

- `main/java/com/example/sporty/features/ai/agent/MatchRecommendAiAgent.java`
  - L25: `import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;`
  - L26: `import com.example.sporty.features.users.domain.entity.Gender;`
  - L27: `import com.example.sporty.features.users.service.UserService;`
- `test/java/com/example/sporty/features/ai/agent/MatchRecommendAiAgentTest.java`
  - L33: `import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;`
  - L34: `import com.example.sporty.features.users.domain.entity.Gender;`
  - L35: `import com.example.sporty.features.users.service.UserService;`

## 알려진 사항

- 서비스에 복사된 `SecurityConfig`/`JwtAuthenticationFilter` 때문에 X-User-Id 필터(W-11) 전까지 `/actuator/health` 가 401 일 수 있다. JWT 검증과 CORS 는 gateway(W-12)로 옮길 대상이다.
- 서비스의 `application.yml` 은 골격 PR 의 것을 그대로 두었다. 1차 `application-dev.yml` 의 값은 W-24(Config 이전)에서 옮긴다.
- `backend/sporty` 는 그대로 있고 계속 실행·빌드된다. 서비스 이동이 끝나면(W-07 ~ W-10) 지운다.
- `ApiIntegrationTest`, `SportyApplicationTests` 는 모놀리스 전체를 띄우므로 복사하지 않았다.

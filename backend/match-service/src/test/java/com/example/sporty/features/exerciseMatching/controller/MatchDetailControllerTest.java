package com.example.sporty.features.exerciseMatching.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.facilities.repository.ServiceRepository;
import com.example.sporty.support.FacilityFixtures;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// 실제 JWT 필터 -> Controller -> Service -> JSON 응답을 검증하며 DB 조회만 대체한다.
@WebMvcTest(MatchController.class)
@Import({MatchService.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes")
class MatchDetailControllerTest {

    private static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchRepository matchRepository;

    @MockitoBean
    private MatchParticipantRepository matchParticipantRepository;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ServiceRepository serviceRepository;

    @Test
    @DisplayName("[TC-EM03-01] 비로그인 상세 조회 200, 기본 정보와 참가자 프로필 반환")
    void anonymousUserCanReadMatchDetail() throws Exception {
        givenMatch();

        mvc.perform(get("/api/matches/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(101))
                .andExpect(jsonPath("$.title").value("주말 풋살 모집"))
                .andExpect(jsonPath("$.description").value("함께 풋살하실 분"))
                .andExpect(jsonPath("$.startAt").value("2026-09-26T19:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-09-26T21:00:00"))
                .andExpect(jsonPath("$.maxParticipant").value(10))
                .andExpect(jsonPath("$.currentParticipantCount").value(2))
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.skillLevel").value("BEGINNER"))
                .andExpect(jsonPath("$.sportType").value("FUTSAL"))
                .andExpect(jsonPath("$.genderGroup").value("MIXED"))
                .andExpect(jsonPath("$.serviceId").value(7))
                .andExpect(jsonPath("$.serviceName").value("Test service 7"))
                .andExpect(jsonPath("$.locationName").value("Test location"))
                .andExpect(jsonPath("$.region").value("강남구"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].profileId").value(401))
                .andExpect(jsonPath("$.participants[0].nickname").value("생성자"))
                .andExpect(jsonPath("$.participants[0].imageUrl").value("https://example.com/owner.png"))
                .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.participants[1].role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.participants[0].email").doesNotExist())
                .andExpect(jsonPath("$.participants[0].password").doesNotExist())
                .andExpect(jsonPath("$.participants[0].user").doesNotExist())
                .andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.isParticipant").value(false));
    }

    @ParameterizedTest
    @CsvSource({"1,true,true", "2,false,true", "3,false,false"})
    @DisplayName("유효한 JWT의 사용자 ID와 매치 참가 역할로 내 상태를 반환한다")
    void authenticatedUserGetsOwnState(String userId, boolean owner, boolean participant) throws Exception {
        givenMatch();

        mvc.perform(get("/api/matches/101").header("Authorization", "Bearer " +
                        token(userId, SECRET, Instant.now().plusSeconds(300))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(owner))
                .andExpect(jsonPath("$.isParticipant").value(participant));

        // 같은 처리 스레드의 다음 비로그인 요청에 이전 사용자 상태가 남지 않아야 한다.
        mvc.perform(get("/api/matches/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.isParticipant").value(false));
    }

    @Test
    @DisplayName("잘못된 토큰과 만료 토큰으로도 공개 상세는 조회하되 사용자로 인증하지 않는다")
    void invalidTokensAreTreatedAsAnonymousForDetail() throws Exception {
        givenMatch();
        List<String> headers = List.of("Bearer broken", "Bearer ", "Basic ignored",
                "Bearer " + token("1", SECRET, Instant.now().minusSeconds(60)),
                "Bearer " + token("1", "different-test-secret-with-at-least-32-bytes", Instant.now().plusSeconds(300)),
                "Bearer " + token("invalid-user-id", SECRET, Instant.now().plusSeconds(300)));

        for (String header : headers) {
            mvc.perform(get("/api/matches/101").header("Authorization", header))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOwner").value(false))
                    .andExpect(jsonPath("$.isParticipant").value(false));
        }
    }

    @Test
    @DisplayName("로그인에서 발급하는 Access Token은 사용자를 식별하고 Refresh Token은 비로그인으로 처리한다")
    void issuedTokensFollowDetailAuthenticationPolicy() throws Exception {
        givenMatch();
        JwtProvider jwtProvider = new JwtProvider(SECRET);

        mvc.perform(get("/api/matches/101").header("Authorization", "Bearer " +
                        jwtProvider.createAccessToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.isParticipant").value(true));

        mvc.perform(get("/api/matches/101").header("Authorization", "Bearer " +
                        jwtProvider.createRefreshToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.isParticipant").value(false));
    }

    @Test
    @DisplayName("[TC-EM03-02] 존재하지 않는 매치는 404와 공통 오류 메시지를 반환한다")
    void missingMatchReturnsNotFoundMessage() throws Exception {
        when(matchRepository.findById(999999L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/matches/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("매치를 찾을 수 없습니다."));
        verifyNoInteractions(matchParticipantRepository, profileRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "9223372036854775808"})
    @DisplayName("Long으로 해석할 수 없는 matchId는 400을 반환한다")
    void malformedMatchIdReturnsBadRequest(String matchId) throws Exception {
        mvc.perform(get("/api/matches/" + matchId)).andExpect(status().isBadRequest());
        verifyNoInteractions(matchRepository, matchParticipantRepository, profileRepository);
    }

    @Test
    @DisplayName("Integer 범위를 넘는 매치와 사용자 ID로 상세 조회 및 생성자 확인이 가능하다")
    void longIdsRetainMatchDetailsAndOwnerState() throws Exception {
        Long matchId = 2147483648L;
        Long userId = 2147483649L;
        Long serviceId = 2147483650L;
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(MatchEntity.builder()
                .id(matchId).service(FacilityFixtures.service(serviceId, true)).build()));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId)).thenReturn(List.of(
                MatchParticipantEntity.builder().id(2147483651L)
                        .user(UserEntity.builder().id(userId).build())
                        .role(MatchParticipantRole.OWNER).build()));
        when(profileRepository.findAllByUser_IdIn(List.of(userId))).thenReturn(List.of(
                ProfileEntity.builder().id(401L).nickname("생성자")
                        .user(UserEntity.builder().id(userId).build()).build()));

        mvc.perform(get("/api/matches/{matchId}", matchId)
                        .header("Authorization", "Bearer " +
                                token(userId.toString(), SECRET, Instant.now().plusSeconds(300))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.participants[0].profileId").value(401))
                .andExpect(jsonPath("$.participants[0].nickname").value("생성자"))
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.isParticipant").value(true));
    }

    private void givenMatch() {
        when(matchRepository.findById(101L)).thenReturn(Optional.of(MatchEntity.builder()
                .id(101L).title("주말 풋살 모집").description("함께 풋살하실 분")
                .startAt(LocalDateTime.of(2026, 9, 26, 19, 0))
                .endAt(LocalDateTime.of(2026, 9, 26, 21, 0))
                .maxParticipant(10).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).service(FacilityFixtures.service(7L, true)).build()));
        when(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(101L)).thenReturn(List.of(
                MatchParticipantEntity.builder().id(11L).user(UserEntity.builder().id(1L).build())
                        .role(MatchParticipantRole.OWNER).build(),
                MatchParticipantEntity.builder().id(12L).user(UserEntity.builder().id(2L).build())
                        .role(MatchParticipantRole.PARTICIPANT).build()));
        when(profileRepository.findAllByUser_IdIn(List.of(1L, 2L))).thenReturn(List.of(
                ProfileEntity.builder().id(401L).nickname("생성자").imageUrl("https://example.com/owner.png")
                        .user(UserEntity.builder().id(1L).email("owner@example.com").password("test-hash").build()).build(),
                ProfileEntity.builder().id(402L).nickname("참가자")
                        .user(UserEntity.builder().id(2L).build()).build()));
    }

    @ParameterizedTest
    @EnumSource(GenderGroup.class)
    void creationPassesGenderGroupToPersistence(GenderGroup genderGroup) throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(UserEntity.builder().id(1L).build()));
        when(serviceRepository.findById(7L)).thenReturn(Optional.of(FacilityFixtures.service(7L, true)));
        when(matchRepository.save(any(MatchEntity.class))).thenAnswer(invocation ->
                MatchEntity.builder().id(101L).genderGroup(invocation.<MatchEntity>getArgument(0).getGenderGroup()).build());

        mvc.perform(post("/api/matches")
                .header("Authorization", "Bearer " + new JwtProvider(SECRET).createAccessToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(", \"genderGroup\": \"" + genderGroup.name() + "\"")))
                .andExpect(status().isCreated());
        verify(matchRepository).save(argThat(match -> match.getGenderGroup() == genderGroup
                && match.getService().getId().equals(7L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ", \"genderGroup\": null", ", \"genderGroup\": \"UNKNOWN\""})
    void creationRejectsMissingNullOrInvalidGenderGroup(String genderField) throws Exception {
        mvc.perform(post("/api/matches")
                .header("Authorization", "Bearer " + new JwtProvider(SECRET).createAccessToken(1L))
                .contentType(MediaType.APPLICATION_JSON).content(createRequest(genderField)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(matchRepository, matchParticipantRepository, userRepository, serviceRepository);
    }

    @ParameterizedTest
    @EnumSource(GenderGroup.class)
    void searchBindsGenderGroupAndReturnsIt(GenderGroup genderGroup) throws Exception {
        when(matchRepository.searchMatches(
                null, null, null, null, null, null, null, null, genderGroup,
                null, null, null)) // status, region, isFree 미지정
                .thenReturn(List.of(MatchEntity.builder().id(101L).genderGroup(genderGroup).service(FacilityFixtures.service(7L, true)).build()));
        mvc.perform(get("/api/matches").param("genderGroup", genderGroup.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].genderGroup").value(genderGroup.name()));
        verify(matchRepository).searchMatches(
                null, null, null, null, null, null, null, null, genderGroup,
                null, null, null); // status, region, isFree 미지정
    }

    @Test
    void searchAllowsOmittedGenderGroup() throws Exception {
        mvc.perform(get("/api/matches")).andExpect(status().isOk());
        verify(matchRepository).searchMatches(
                null, null, null, null, null, null, null, null, null,
                null, null, null); // status, region, isFree 미지정
    }

    @Test
    void searchRejectsInvalidGenderGroup() throws Exception {
        mvc.perform(get("/api/matches").param("genderGroup", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(matchRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void searchBindsAndSerializesBooleanIsFree(boolean isFree) throws Exception {
        when(matchRepository.searchMatches(
                null, null, null, null, null, null, null, null, null,
                null, "강남구", isFree))
                .thenReturn(List.of(MatchEntity.builder().id(101L)
                        .service(FacilityFixtures.service(7L, isFree)).build()));

        mvc.perform(get("/api/matches").param("region", "강남구")
                        .param("isFree", Boolean.toString(isFree)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isFree").isBoolean())
                .andExpect(jsonPath("$[0].isFree").value(isFree))
                .andExpect(jsonPath("$[0].region").value("강남구"));
    }

    private String createRequest(String genderField) {
        return """
                {"serviceId":7,"title":"Test match","description":"Training",
                 "startAt":"2026-10-10T18:00:00","endAt":"2026-10-10T20:00:00",
                 "maxParticipant":10,"skillLevel":"BEGINNER","sportType":"FUTSAL"%s}
                """.formatted(genderField);
    }

    private String token(String userId, String secret, Instant expiration) {
        // 전역 권한 문자열이 OWNER여도 해당 매치에 참여하지 않았다면 생성자가 아니다.
        return Jwts.builder().setSubject(userId).claim("role", "OWNER")
                .claim("tokenType", "ACCESS")
                .setExpiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

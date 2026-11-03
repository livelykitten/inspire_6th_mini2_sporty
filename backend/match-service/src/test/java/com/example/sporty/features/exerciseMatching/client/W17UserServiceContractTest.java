package com.example.sporty.features.exerciseMatching.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import com.example.sporty.features.commons.handler.GlobalExceptionHandler;
import com.example.sporty.features.exerciseMatching.controller.MatchController;
import com.example.sporty.features.exerciseMatching.domain.dto.*;
import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.repository.*;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.support.FacilityFixtures;
import feign.*;
import io.github.resilience4j.circuitbreaker.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.openfeign.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;

/** No application context, discovery server, database, or remote HTTP service is started.
 * Fallback invocation is explicit: these tests do not prove Feign/CB runtime wiring.
 */
@ExtendWith(MockitoExtension.class)
class W17UserServiceContractTest {
    @Mock UserServiceClient client;
    @Mock MatchRepository matches;
    @Mock MatchParticipantRepository participants;
    @Mock FacilityServiceSnapshotRepository facilities;
    MemberLookupImpl lookup;
    MatchService service;
    MockMvc mvc;
    final UserServiceFallbackFactory factory = new UserServiceFallbackFactory();
    static final List<Long> IDS = List.of(7L, 8L);

    @BeforeEach
    void setUp() {
        lookup = new MemberLookupImpl(client);
        service = new MatchService(matches, participants, lookup, facilities);
        mvc = MockMvcBuilders.standaloneSetup(new MatchController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    static FeignException http(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://user-service/internal/users",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("UserServiceClient", Response.builder()
                .request(request).status(status).reason("test").headers(Map.of()).build());
    }

    static Stream<Throwable> unavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "http://user-service/internal/users",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return Stream.of(
                new RetryableException(-1, "connection failed", Request.HttpMethod.GET,
                        new ConnectException("refused"), (Long) null, request),
                new RetryableException(-1, "timed out", Request.HttpMethod.GET,
                        new SocketTimeoutException("timeout"), (Long) null, request),
                http(503),
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test")));
    }

    @Test
    void feignDeclarationMatchesW16Endpoints() throws Exception {
        var annotation = UserServiceClient.class.getAnnotation(FeignClient.class);
        assertThat(annotation.name()).isEqualTo("user-service");
        assertThat(annotation.fallbackFactory()).isEqualTo(UserServiceFallbackFactory.class);
        var status = UserServiceClient.class.getMethod("getUserStatus", Long.class);
        assertThat(status.getAnnotation(GetMapping.class).value()).containsExactly("/internal/users/{userId}/status");
        assertThat(((PathVariable) status.getParameterAnnotations()[0][0]).value()).isEqualTo("userId");
        var profiles = UserServiceClient.class.getMethod("getUserProfiles", List.class);
        assertThat(profiles.getAnnotation(GetMapping.class).value()).containsExactly("/internal/users/profiles");
        assertThat(profiles.getAnnotation(org.springframework.cloud.openfeign.CollectionFormat.class).value())
                .isEqualTo(feign.CollectionFormat.CSV);
        assertThat(((RequestParam) profiles.getParameterAnnotations()[0][0]).value()).isEqualTo("userIds");
    }

    @Test
    void activePasses() {
        when(client.getUserStatus(7L)).thenReturn(new UserStatusResponseDto(7L, "ACTIVE", null));
        assertThatCode(() -> lookup.validateActive(7L)).doesNotThrowAnyException();
        verify(client).getUserStatus(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WITHDRAWAL_PENDING", "WITHDRAWN"})
    void withdrawnReturns401(String status) throws Exception {
        when(client.getUserStatus(null)).thenReturn(new UserStatusResponseDto(7L, status, "saga"));
        mvc.perform(get("/api/matches/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
        verifyNoInteractions(matches, participants, facilities);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "join"})
    void missingUserInEm01AndEm06Returns401(String endpoint) throws Exception {
        when(client.getUserStatus(null)).thenAnswer(invocation -> factory.create(http(404)).getUserStatus(null));
        if (endpoint.equals("join")) {
            when(matches.findByIdForUpdate(101L)).thenReturn(Optional.of(mock(MatchEntity.class)));
        }
        mvc.perform(requestFor(endpoint)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MATCH_USER_NOT_FOUND"));
        verify(participants, never()).save(any());
        verify(matches, never()).save(any());
    }

    @Test
    void missingUserInEm08MustReturn404UserNotFound() throws Exception {
        when(client.getUserStatus(null)).thenAnswer(invocation -> factory.create(http(404)).getUserStatus(null));
        // Deliberately retains the requested contract: current implementation returns 401.
        mvc.perform(get("/api/matches/me")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @ParameterizedTest
    @MethodSource("unavailable")
    void statusUnavailableReturns503(Throwable cause) throws Exception {
        when(client.getUserStatus(null)).thenAnswer(invocation -> factory.create(cause).getUserStatus(null));
        mvc.perform(get("/api/matches/me")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE"));
        verifyNoInteractions(matches, participants, facilities);
    }

    @Test
    void status500IsNotMisclassifiedAsUnavailable() throws Exception {
        var cause = http(500);
        assertThatThrownBy(() -> factory.create(cause).getUserStatus(7L))
                .isInstanceOf(IllegalStateException.class).hasCause(cause);
        when(client.getUserStatus(null)).thenAnswer(invocation -> factory.create(cause).getUserStatus(null));
        mvc.perform(get("/api/matches/me")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void profilesAreKeyedByUserIdRatherThanProfileIdOrResponseOrder() {
        when(client.getUserProfiles(IDS)).thenReturn(List.of(
                new UserProfileResponseDto(8L, 108L, "second", null),
                new UserProfileResponseDto(7L, 107L, "first", "image")));
        assertThat(lookup.getProfiles(IDS)).containsExactlyInAnyOrderEntriesOf(Map.of(
                7L, new MemberProfile(107L, "first", "image"),
                8L, new MemberProfile(108L, "second", null)));
        verify(client).getUserProfiles(IDS);
    }

    @ParameterizedTest
    @MethodSource("unavailable")
    void profileUnavailableGivesEmptyMapAndEm03StillReturns200(Throwable cause) throws Exception {
        when(client.getUserProfiles(IDS)).thenAnswer(invocation -> factory.create(cause).getUserProfiles(IDS));
        assertThat(lookup.getProfiles(IDS)).isEmpty();
        givenDetail();
        mvc.perform(get("/api/matches/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipantCount").value(2))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.participants[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.participants[0].profileId").doesNotExist())
                .andExpect(jsonPath("$.participants[1].imageUrl").doesNotExist());
    }

    @Test
    void profile500MustNotBecomeEmptySuccess() throws Exception {
        var cause = http(500);
        assertThatThrownBy(() -> factory.create(cause).getUserProfiles(IDS))
                .isInstanceOf(IllegalStateException.class).hasCause(cause);
        when(client.getUserProfiles(IDS)).thenAnswer(invocation -> factory.create(cause).getUserProfiles(IDS));
        assertThatThrownBy(() -> lookup.getProfiles(IDS)).hasCause(cause);
        givenDetail();
        mvc.perform(get("/api/matches/101")).andExpect(status().isInternalServerError());
    }

    void givenDetail() {
        var match = MatchEntity.builder().id(101L).title("test")
                .service(FacilityFixtures.service(1L, true)).build();
        when(matches.findById(101L)).thenReturn(Optional.of(match));
        when(participants.findAllByMatch_IdOrderByIdAsc(101L)).thenReturn(List.of(
                MatchParticipantEntity.builder().match(match).userId(7L).role(MatchParticipantRole.OWNER).build(),
                MatchParticipantEntity.builder().match(match).userId(8L).role(MatchParticipantRole.PARTICIPANT).build()));
    }

    MockHttpServletRequestBuilder requestFor(String endpoint) {
        if (endpoint.equals("join")) return post("/api/matches/101/participants");
        return post("/api/matches").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceId":1,"title":"test","startAt":"2099-01-01T10:00:00",
                 "endAt":"2099-01-01T11:00:00","maxParticipant":4,
                 "skillLevel":"BEGINNER","sportType":"SOCCER","genderGroup":"MIXED"}
                """);
    }
}

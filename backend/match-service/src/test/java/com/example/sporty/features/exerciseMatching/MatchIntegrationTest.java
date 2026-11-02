package com.example.sporty.features.exerciseMatching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;

/** Real MariaDB queries; fixtures are flushed, reloaded, and rolled back after each test. */
@Tag("integration")
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Import(MatchService.class)
class MatchIntegrationTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 18, 0);
    private Long serviceId;

    @Autowired private TestEntityManager em;
    @Autowired private MatchService service;
    @Autowired private MatchParticipantRepository participants;
    @Autowired private ProfileRepository profiles;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void createFacilities() {
        serviceId = FacilityFixtures.newServiceId();
        FacilityFixtures.create(em.getEntityManager(), serviceId);
        FacilityFixtures.create(em.getEntityManager(), serviceId + 1);
    }

    @Test
    void databaseRejectsMissingServiceAndDeletionOfReferencedService() {
        MatchEntity match = match(UUID.randomUUID().toString(), "references", serviceId, START, 10, SkillLevel.BEGINNER);
        reload();
        jdbc.update("DELETE FROM service WHERE id=?", serviceId + 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE `match` SET service_id=? WHERE id=?", serviceId + 1, match.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM service WHERE id=?", serviceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateParticipantsEvenWhenBypassingTheService() {
        MatchEntity match = match(UUID.randomUUID().toString(), "constraints", serviceId, START, 10, SkillLevel.BEGINNER);
        UserEntity user = user();
        participant(match, user, MatchParticipantRole.OWNER);
        reload();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO match_participant (match_id,user_id,role,created_at,updated_at) VALUES (?,?,'PARTICIPANT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                match.getId(), user.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(participants.countByMatch_Id(match.getId())).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicateSportPreferences() {
        UserEntity user = user();
        ProfileEntity profile = em.persist(ProfileEntity.builder().user(user)
                .nickname(UUID.randomUUID().toString()).district(District.GANGNAM).build());
        reload();
        String insert = "INSERT INTO sport_preference (profile_id,sport_type,created_at,updated_at) VALUES (?,'FUTSAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        jdbc.update(insert, profile.getId());
        assertThatThrownBy(() -> jdbc.update(insert, profile.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMissingRequiredMatchFieldsAndInvalidBoundaries() {
        MatchEntity match = match(UUID.randomUUID().toString(), "constraints", serviceId, START, 10, SkillLevel.BEGINNER);
        reload();
        for (String assignment : List.of("title=NULL", "title=' '", "start_at=NULL", "end_at=NULL",
                "max_participant=NULL", "max_participant=0", "end_at=start_at", "service_id=NULL", "service_id=0", "gender_group=NULL")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE `match` SET " + assignment + " WHERE id=?", match.getId()))
                    .as(assignment).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void searchAppliesAllFiltersIncludingLongServiceIdAndInclusiveBoundaries() {
        String marker = UUID.randomUUID().toString();
        MatchEntity expected = match(marker, "Training", serviceId, START, 10, SkillLevel.BEGINNER);
        match(marker, "Training", serviceId + 1, START, 10, SkillLevel.BEGINNER);
        match(marker, "Training", serviceId, START.minusHours(1), 10, SkillLevel.BEGINNER);
        match(marker, "Training", serviceId, START.plusHours(1), 10, SkillLevel.BEGINNER);
        match(marker, "Training", serviceId, START, 11, SkillLevel.BEGINNER);
        match(marker, "Training", serviceId, START, 10, SkillLevel.INTERMEDIATE);
        match(marker, "Other", serviceId, START, 10, SkillLevel.BEGINNER);
        reload();

        var result = service.searchMatches(MatchSearchRequestDto.builder()
                .serviceId(serviceId).titleKeyword(marker).descriptionKeyword("training")
                .startAt(START).endAt(START.plusHours(2)).maxParticipant(10)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL).build());

        assertThat(result).extracting(MatchResponseDto::getMatchId).containsExactly(expected.getId());
    }

    @Test
    void searchTreatsPercentUnderscoreAndEscapeCharacterAsLiteralText() {
        String marker = UUID.randomUUID().toString();
        MatchEntity expected = match(marker + "%_!", "Drill %_!", serviceId, START, 10, SkillLevel.BEGINNER);
        match(marker + "abc!", "Drill abc!", serviceId, START, 10, SkillLevel.BEGINNER);
        reload();

        var result = service.searchMatches(MatchSearchRequestDto.builder()
                .titleKeyword(marker + "%_!").descriptionKeyword("drill %_!").build());

        assertThat(result).extracting(MatchResponseDto::getMatchId).containsExactly(expected.getId());
    }

    @Test
    void participantsAndProfilesReloadInOrderAndDetailIncludesMissingProfile() {
        MatchEntity match = match(UUID.randomUUID().toString(), "Integration", serviceId, START, 10, SkillLevel.BEGINNER);
        MatchEntity otherMatch = match(UUID.randomUUID().toString(), "Other", serviceId, START, 10, SkillLevel.BEGINNER);
        UserEntity owner = user();
        UserEntity guest = user();
        ProfileEntity profile = em.persist(ProfileEntity.builder().user(owner)
                .nickname(UUID.randomUUID().toString()).district(District.GANGNAM).build());
        MatchParticipantEntity first = participant(match, owner, MatchParticipantRole.OWNER);
        MatchParticipantEntity second = participant(match, guest, MatchParticipantRole.PARTICIPANT);
        participant(otherMatch, owner, MatchParticipantRole.OWNER);
        reload();

        assertThat(participants.findAllByMatch_IdOrderByIdAsc(match.getId()))
                .extracting(MatchParticipantEntity::getId).containsExactly(first.getId(), second.getId());
        assertThat(participants.countByMatch_Id(match.getId())).isEqualTo(2);
        assertThat(participants.existsByMatch_IdAndUserId(match.getId(), guest.getId())).isTrue();
        assertThat(participants.existsByMatch_IdAndUserId(otherMatch.getId(), guest.getId())).isFalse();
        assertThat(participants.findByMatch_IdAndUserId(match.getId(), owner.getId()))
                .hasValueSatisfying(p -> assertThat(p.getRole()).isEqualTo(MatchParticipantRole.OWNER));
        assertThat(profiles.findAllByUser_IdIn(List.of(owner.getId(), guest.getId())))
                .extracting(ProfileEntity::getId).containsExactly(profile.getId());

        reload();
        var detail = service.getMatchDetail(match.getId(), owner.getId());
        assertThat(detail.getGenderGroup()).isEqualTo(GenderGroup.MIXED);
        assertThat(detail.getCurrentParticipantCount()).isEqualTo(2);
        assertThat(detail.getIsOwner()).isTrue();
        assertThat(detail.getIsParticipant()).isTrue();
        assertThat(detail.getParticipants().get(0).getProfileId()).isEqualTo(profile.getId());
        assertThat(detail.getParticipants().get(1).getProfileId()).isNull();
        assertThat(detail.getParticipants().get(1).getRole()).isEqualTo(MatchParticipantRole.PARTICIPANT);
    }

    private MatchEntity match(String title, String description, Long serviceId,
            LocalDateTime start, int capacity, SkillLevel skill) {
        return match(title, description, serviceId, start, capacity, skill, GenderGroup.MIXED);
    }

    private MatchEntity match(String title, String description, Long serviceId,
            LocalDateTime start, int capacity, SkillLevel skill, GenderGroup genderGroup) {
        return em.persist(MatchEntity.builder().title(title).description(description)
                .service(em.getEntityManager().getReference(ServiceEntity.class, serviceId)).startAt(start).endAt(start.plusHours(2))
                .maxParticipant(capacity).skillLevel(skill).sportType(SportType.FUTSAL)
                .genderGroup(genderGroup).build());
    }

    @Test
    void searchFiltersEachGenderGroupAndReturnsAllWhenOmitted() {
        String marker = UUID.randomUUID().toString();
        for (GenderGroup genderGroup : GenderGroup.values()) {
            match(marker, "Gender filter", serviceId, START, 10, SkillLevel.BEGINNER, genderGroup);
        }
        reload();

        for (GenderGroup genderGroup : GenderGroup.values()) {
            var result = service.searchMatches(MatchSearchRequestDto.builder()
                    .titleKeyword(marker).genderGroup(genderGroup).build());
            assertThat(result).extracting(MatchResponseDto::getGenderGroup).containsExactly(genderGroup);
        }
        assertThat(service.searchMatches(MatchSearchRequestDto.builder().titleKeyword(marker).build()))
                .extracting(MatchResponseDto::getGenderGroup)
                .containsExactlyInAnyOrder(GenderGroup.values());
    }

    private UserEntity user() {
        return em.persist(UserEntity.builder().email(UUID.randomUUID() + "@test.invalid")
                .password("integration-test-only").gender(Gender.MALE).build());
    }

    private MatchParticipantEntity participant(MatchEntity match, UserEntity user, MatchParticipantRole role) {
        return em.persist(MatchParticipantEntity.builder().match(match).user(user).role(role).build());
    }

    private void reload() {
        em.flush();
        em.clear();
    }
}

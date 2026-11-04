package com.example.sporty.features.exerciseMatching.service;

import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.domain.enums.*;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.support.MatchUserFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.sporty.features.exerciseMatching.client.MemberLookup;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.auto_quote_keyword=true",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema-h2.sql",
    "test.context-id=my-matches"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({MatchService.class, com.example.sporty.features.exerciseMatching.withdrawal.MatchUserGuardService.class})
class MyMatchesTest {
    @Autowired EntityManager em;
    @Autowired MatchService service;
    @Autowired MatchParticipantRepository participants;
    @MockitoBean MemberLookup memberLookup;

    @Test
    void returnsOnlyOwnMatchesWithRoleCountsAndOrder() {
        var users = MatchUserFixtures.create(em, 2);
        var facility = FacilityFixtures.create(em, FacilityFixtures.newServiceId());
        var created = match(facility, "created", 1);
        var joined = match(facility, "joined", 2);
        var unrelated = match(facility, "unrelated", 3);
        participate(created, users.get(1L), MatchParticipantRole.OWNER);
        participate(created, users.get(2L), MatchParticipantRole.PARTICIPANT);
        participate(joined, users.get(2L), MatchParticipantRole.OWNER);
        participate(joined, users.get(1L), MatchParticipantRole.PARTICIPANT);
        participate(unrelated, users.get(2L), MatchParticipantRole.OWNER);
        em.flush(); em.clear();

        var result = service.getMyMatches(users.get(1L));
        assertThat(result).extracting(dto -> dto.getTitle()).containsExactly("joined", "created");
        assertThat(result).extracting(dto -> dto.getRole())
                .containsExactly(MatchParticipantRole.PARTICIPANT, MatchParticipantRole.OWNER);
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.getNumCurrentParticipant()).isEqualTo(2);
            assertThat(dto.getRegion()).isEqualTo("강남구");
            assertThat(dto.getFacilityName()).isNotBlank();
        });

        participants.delete(participants.findByMatch_IdAndUserId(joined.getId(), users.get(1L)).orElseThrow());
        em.flush(); em.clear();
        assertThat(service.getMyMatches(users.get(1L))).extracting(dto -> dto.getTitle()).containsExactly("created");
    }

    @Test
    void returnsEmptyListWhenNoParticipation() {
        var users = MatchUserFixtures.create(em, 1);
        assertThat(service.getMyMatches(users.get(1L))).isEmpty();
    }

    private MatchEntity match(FacilityServiceSnapshotEntity facility, String title, int days) {
        var start = LocalDateTime.now().plusDays(days);
        var match = MatchEntity.builder().service(facility).title(title).startAt(start).endAt(start.plusHours(1))
                .maxParticipant(10).sportType(SportType.SOCCER).genderGroup(GenderGroup.MIXED).build();
        em.persist(match);
        return match;
    }

    private void participate(MatchEntity match, Long userId, MatchParticipantRole role) {
        em.persist(MatchParticipantEntity.builder().match(match).userId(userId).role(role).build());
    }
}

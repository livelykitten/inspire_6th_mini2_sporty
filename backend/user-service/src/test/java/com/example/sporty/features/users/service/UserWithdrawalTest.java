package com.example.sporty.features.users.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.commons.exception.users.PasswordMismatchException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.domain.enums.*;
import com.example.sporty.features.exerciseMatching.repository.*;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.features.users.domain.entity.*;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.support.FacilityFixtures;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.auto_quote_keyword=true"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@Import({UserService.class, MatchService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class UserWithdrawalTest {
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired MatchRepository matches;
    @Autowired MatchParticipantRepository participants;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean PasswordEncoder passwords;
    @MockitoBean RefreshTokenService tokens;
    long userId, owned, ownedPast, joined, joinedPast, unrelated;

    @BeforeEach void setup() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            participants.deleteAllInBatch(); matches.deleteAllInBatch();
            UserEntity user=users.save(UserEntity.builder().email(UUID.randomUUID()+"@test.io").password("hash").gender(Gender.MALE).build());
            UserEntity other=users.save(UserEntity.builder().email(UUID.randomUUID()+"@test.io").password("hash").gender(Gender.MALE).build());
            userId=user.getId();
            var facility=FacilityFixtures.create(em,FacilityFixtures.newServiceId());
            for(int i=0;i<5;i++) {
                boolean past=i==1||i==3;
                var start=LocalDateTime.now().plusDays(past?-2:2);
                var match=matches.save(MatchEntity.builder().title("Withdrawal fixture "+i).service(facility).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).maxParticipant(2).status(MatchStatus.CLOSED).startAt(start).endAt(start.plusHours(1)).build());
                participants.save(MatchParticipantEntity.builder().match(match).user(i<2?user:other).role(MatchParticipantRole.OWNER).build());
                participants.save(MatchParticipantEntity.builder().match(match).user(i<2?other:(i<4?user:users.save(UserEntity.builder().email(UUID.randomUUID()+"@test.io").password("hash").gender(Gender.MALE).build()))).role(MatchParticipantRole.PARTICIPANT).build());
                switch(i){case 0:owned=match.getId();break;case 1:ownedPast=match.getId();break;case 2:joined=match.getId();break;case 3:joinedPast=match.getId();break;default:unrelated=match.getId();}
            }
        });
        when(passwords.matches("correct","hash")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId,null,List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void removesOwnedMatchesAndOnlyOwnParticipationElsewhere() {
        service.withdrawal("correct");
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(users.findById(userId).orElseThrow().getWithdrawnAt()).isNotNull();
        for(long id:List.of(owned,ownedPast)){assertThat(matches.existsById(id)).isFalse();assertThat(participants.countByMatch_Id(id)).isZero();}
        for(long id:List.of(joined,joinedPast)){assertThat(matches.existsById(id)).isTrue();assertThat(participants.countByMatch_Id(id)).isEqualTo(1);assertThat(participants.existsByMatch_IdAndUserId(id,userId)).isFalse();}
        assertThat(matches.findById(joined).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(matches.findById(joinedPast).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(participants.countByMatch_Id(unrelated)).isEqualTo(2);
        assertThat(matches.findById(unrelated).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
        verify(tokens).delete(userId);
    }
    @Test void wrongOrMissingPasswordDoesNotDeleteAnything() {
        for(String password:new String[]{null,"","wrong"})assertThatThrownBy(()->service.withdrawal(password)).isInstanceOf(PasswordMismatchException.class);
        assertOriginal();verifyNoInteractions(tokens);
    }
    @Test void failureAfterDeletesRollsBackDatabaseChanges() {
        doAnswer(invocation->{em.flush();assertThat(matches.existsById(owned)).isFalse();throw new IllegalStateException("token store failure");}).when(tokens).delete(userId);
        assertThatThrownBy(()->service.withdrawal("correct")).isInstanceOf(IllegalStateException.class);
        assertOriginal();
    }
    @Test void userWithoutMatchesCanWithdraw() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{participants.deleteAllInBatch();matches.deleteAllInBatch();});
        service.withdrawal("correct");
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }
    void assertOriginal(){
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(matches.count()).isEqualTo(5);assertThat(participants.count()).isEqualTo(10);
        assertThat(matches.findById(joined).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
    }
}

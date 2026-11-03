package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sporty.features.exerciseMatching.domain.entity.FacilityServiceSnapshotEntity;
import com.example.sporty.features.exerciseMatching.event.FacilityServiceChangedEvent;
import com.example.sporty.features.exerciseMatching.repository.FacilityServiceSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class FacilitySnapshotServiceTest {

    @Mock
    private FacilityServiceSnapshotRepository repository;

    private FacilitySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new FacilitySnapshotService(repository);
    }

    @Test
    void insertsSnapshotWhenFacilityDoesNotExist() {
        FacilityServiceChangedEvent event = event(LocalDateTime.of(2026, 11, 3, 10, 30), false);
        when(repository.findById(101L)).thenReturn(Optional.empty());

        service.apply(event);

        ArgumentCaptor<FacilityServiceSnapshotEntity> captor =
                ArgumentCaptor.forClass(FacilityServiceSnapshotEntity.class);
        verify(repository).save(captor.capture());

        FacilityServiceSnapshotEntity saved = captor.getValue();
        assertThat(saved.getServiceId()).isEqualTo(101L);
        assertThat(saved.getName()).isEqualTo("테스트 풋살장");
        assertThat(saved.getLocationName()).isEqualTo("테스트 체육시설");
        assertThat(saved.getRegion()).isEqualTo("강남구");
        assertThat(saved.getIsFree()).isTrue();
        assertThat(saved.getActive()).isFalse();
        assertThat(saved.getSourceUpdatedAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void updatesSnapshotWhenEventIsNewer() {
        LocalDateTime previousTime = LocalDateTime.of(2026, 11, 3, 10, 0);
        FacilityServiceSnapshotEntity snapshot = snapshot(previousTime, true);
        FacilityServiceChangedEvent event = event(previousTime.plusMinutes(30), false);
        when(repository.findById(101L)).thenReturn(Optional.of(snapshot));

        service.apply(event);

        verify(repository).save(snapshot);
        assertThat(snapshot.getActive()).isFalse();
        assertThat(snapshot.getSourceUpdatedAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void ignoresSnapshotWhenEventIsOlder() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 11, 3, 10, 30);
        FacilityServiceSnapshotEntity snapshot = snapshot(currentTime, true);
        FacilityServiceChangedEvent event = event(currentTime.minusMinutes(1), false);
        when(repository.findById(101L)).thenReturn(Optional.of(snapshot));

        service.apply(event);

        verify(repository, never()).save(snapshot);
        assertThat(snapshot.getActive()).isTrue();
        assertThat(snapshot.getSourceUpdatedAt()).isEqualTo(currentTime);
    }

    private FacilityServiceChangedEvent event(LocalDateTime occurredAt, boolean active) {
        return new FacilityServiceChangedEvent(
                "event-1",
                occurredAt,
                101L,
                "테스트 풋살장",
                "테스트 체육시설",
                "강남구",
                true,
                occurredAt.plusDays(1),
                active);
    }

    private FacilityServiceSnapshotEntity snapshot(LocalDateTime sourceUpdatedAt, boolean active) {
        return FacilityServiceSnapshotEntity.builder()
                .serviceId(101L)
                .name("기존 시설")
                .locationName("기존 장소")
                .region("서초구")
                .isFree(false)
                .active(active)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
    }
}

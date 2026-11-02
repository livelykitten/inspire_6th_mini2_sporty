package com.example.sporty.support;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.persistence.EntityManager;
import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import org.springframework.test.util.ReflectionTestUtils;

public final class FacilityFixtures {
    private FacilityFixtures() {}

    public static long newServiceId() {
        return ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
    }

    public static ServiceEntity create(EntityManager em, long serviceId) {
        return create(em, serviceId, true);
    }

    public static ServiceEntity service(long id, Boolean isFree) {
        ServiceEntity service = ServiceEntity.builder()
                .location(LocationEntity.builder().name("Test location").region("강남구").build())
                .serviceId("TEST-" + id).name("Test service " + id).isFree(isFree).build();
        ReflectionTestUtils.setField(service, "id", id);
        return service;
    }

    public static ServiceEntity create(EntityManager em, long serviceId, Boolean isFree) {
        LocationEntity location = LocationEntity.builder().name("Test location")
                .region("강남구").latitude(new BigDecimal("37.50000000"))
                .longitude(new BigDecimal("127.05000000")).build();
        em.persist(location);
        // Explicit ID also exercises values beyond Integer.MAX_VALUE.
        em.createNativeQuery("INSERT INTO service (id,location_id,service_id,name,is_free,active,created_at,updated_at) VALUES (:id,:location,:externalId,:name,:isFree,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .setParameter("id", serviceId).setParameter("location", location.getId())
                .setParameter("externalId", "TEST-" + UUID.randomUUID())
                .setParameter("isFree", isFree)
                .setParameter("name", "Test service " + UUID.randomUUID()).executeUpdate();
        return em.find(ServiceEntity.class, serviceId);
    }
}

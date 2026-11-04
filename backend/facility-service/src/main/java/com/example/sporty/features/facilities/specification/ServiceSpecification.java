package com.example.sporty.features.facilities.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.sporty.features.facilities.domain.dto.ServiceRequestDto;
import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

public final class ServiceSpecification {

    private ServiceSpecification() {
    }

    public static Specification<ServiceEntity> search(
            ServiceRequestDto request
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isTrue(root.get("active")));

            if (hasText(request.getRegion())
                    || hasText(request.getServiceType())) {
                Join<ServiceEntity, LocationEntity> location =
                        root.join("location");

                if (hasText(request.getRegion())) {
                    predicates.add(
                            criteriaBuilder.equal(
                                    location.get("region"),
                                    request.getRegion().trim()
                            )
                    );
                }

                if (hasText(request.getServiceType())) {
                    predicates.add(
                            criteriaBuilder.equal(
                                    location.get("facilityName"),
                                    request.getServiceType().trim()
                            )
                    );
                }
            }

            if (hasText(request.getServiceName())) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                "%" + request.getServiceName()
                                        .trim()
                                        .toLowerCase() + "%"
                        )
                );
            }

            if (hasText(request.getStatus())) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("status"),
                                request.getStatus().trim()
                        )
                );
            }

            if (request.getStartTime() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("startTime"),
                                request.getStartTime()
                        )
                );
            }

            if (request.getEndTime() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("endTime"),
                                request.getEndTime()
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

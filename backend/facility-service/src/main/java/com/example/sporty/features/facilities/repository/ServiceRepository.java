package com.example.sporty.features.facilities.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

public interface ServiceRepository
        extends JpaRepository<ServiceEntity, Long>,
                JpaSpecificationExecutor<ServiceEntity> {

    Optional<ServiceEntity> findByServiceId(String serviceId);

    @EntityGraph(attributePaths = "location")
    Optional<ServiceEntity> findByIdAndActiveTrue(Long id);

    List<ServiceEntity> findAllByActiveTrue();

    @EntityGraph(attributePaths = "location")
    List<ServiceEntity> findAll(
            Specification<ServiceEntity> specification
    );
}

package com.example.sporty.features.users.repository;

import com.example.sporty.features.users.domain.entity.UserOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserOutboxRepository extends JpaRepository<UserOutbox, Long> {

    List<UserOutbox> findTop100ByStatusOrderByIdAsc(String status);
}
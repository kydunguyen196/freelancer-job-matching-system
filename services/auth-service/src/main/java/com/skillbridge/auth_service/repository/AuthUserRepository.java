package com.skillbridge.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.auth_service.domain.AuthUser;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    boolean existsByEmail(String email);

    Optional<AuthUser> findByEmail(String email);
}

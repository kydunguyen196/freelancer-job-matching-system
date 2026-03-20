package com.skillbridge.contract_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.skillbridge.contract_service.domain.ContractOutboxEvent;

import jakarta.persistence.LockModeType;

public interface ContractOutboxEventRepository extends JpaRepository<ContractOutboxEvent, Long> {

    List<ContractOutboxEvent> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ContractOutboxEvent event where event.id = :id")
    Optional<ContractOutboxEvent> findByIdForUpdate(@Param("id") Long id);
}

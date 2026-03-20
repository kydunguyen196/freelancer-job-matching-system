package com.skillbridge.proposal_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.skillbridge.proposal_service.domain.ProposalOutboxEvent;

import jakarta.persistence.LockModeType;

public interface ProposalOutboxEventRepository extends JpaRepository<ProposalOutboxEvent, Long> {

    List<ProposalOutboxEvent> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ProposalOutboxEvent event where event.id = :id")
    Optional<ProposalOutboxEvent> findByIdForUpdate(@Param("id") Long id);
}

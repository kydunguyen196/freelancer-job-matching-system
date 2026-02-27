package com.skillbridge.proposal_service.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.proposal_service.domain.Proposal;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    boolean existsByJobIdAndFreelancerId(Long jobId, Long freelancerId);

    List<Proposal> findByJobIdOrderByCreatedAtDesc(Long jobId);

    Page<Proposal> findByJobId(Long jobId, Pageable pageable);
}

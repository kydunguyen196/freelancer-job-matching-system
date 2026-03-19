package com.skillbridge.proposal_service.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalStatus;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    boolean existsByJobIdAndFreelancerId(Long jobId, Long freelancerId);

    List<Proposal> findByJobIdOrderByCreatedAtDesc(Long jobId);

    Page<Proposal> findByJobId(Long jobId, Pageable pageable);

    Page<Proposal> findByJobIdAndStatus(Long jobId, ProposalStatus status, Pageable pageable);

    Page<Proposal> findByFreelancerId(Long freelancerId, Pageable pageable);

    Page<Proposal> findByFreelancerIdAndStatus(Long freelancerId, ProposalStatus status, Pageable pageable);

    List<Proposal> findByFreelancerId(Long freelancerId);

    List<Proposal> findByJobIdAndIdNot(Long jobId, Long id);

    List<Proposal> findByClientId(Long clientId);

    long countByFreelancerId(Long freelancerId);

    long countByFreelancerIdAndStatus(Long freelancerId, ProposalStatus status);
}

package com.skillbridge.proposal_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.proposal_service.domain.ProposalCvFile;

public interface ProposalCvFileRepository extends JpaRepository<ProposalCvFile, Long> {

    Optional<ProposalCvFile> findByProposalId(Long proposalId);
}

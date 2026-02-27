package com.skillbridge.contract_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.contract_service.domain.Contract;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findBySourceProposalId(Long sourceProposalId);

    List<Contract> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<Contract> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);
}

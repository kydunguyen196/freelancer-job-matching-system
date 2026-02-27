package com.skillbridge.contract_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skillbridge.contract_service.domain.Milestone;
import com.skillbridge.contract_service.domain.MilestoneStatus;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByContractIdOrderByDueDateAscIdAsc(Long contractId);

    List<Milestone> findByContractIdInOrderByContractIdAscDueDateAscIdAsc(List<Long> contractIds);

    long countByContractIdAndStatusNot(Long contractId, MilestoneStatus status);
}

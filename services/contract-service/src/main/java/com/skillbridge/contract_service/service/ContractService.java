package com.skillbridge.contract_service.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.contract_service.domain.Contract;
import com.skillbridge.contract_service.domain.ContractStatus;
import com.skillbridge.contract_service.domain.Milestone;
import com.skillbridge.contract_service.domain.MilestoneStatus;
import com.skillbridge.contract_service.dto.ContractResponse;
import com.skillbridge.contract_service.dto.CreateContractFromProposalRequest;
import com.skillbridge.contract_service.dto.CreateMilestoneRequest;
import com.skillbridge.contract_service.dto.MilestoneResponse;
import com.skillbridge.contract_service.messaging.MilestoneEventPublisher;
import com.skillbridge.contract_service.repository.ContractRepository;
import com.skillbridge.contract_service.repository.MilestoneRepository;
import com.skillbridge.contract_service.security.JwtUserPrincipal;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneEventPublisher milestoneEventPublisher;

    public ContractService(
            ContractRepository contractRepository,
            MilestoneRepository milestoneRepository,
            MilestoneEventPublisher milestoneEventPublisher
    ) {
        this.contractRepository = contractRepository;
        this.milestoneRepository = milestoneRepository;
        this.milestoneEventPublisher = milestoneEventPublisher;
    }

    @Transactional
    public ContractResponse createContractFromProposal(CreateContractFromProposalRequest request) {
        validateCreateContractRequest(request);
        if (request.clientId().equals(request.freelancerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId and freelancerId must be different");
        }

        Contract existing = contractRepository.findBySourceProposalId(request.proposalId()).orElse(null);
        if (existing != null) {
            List<Milestone> existingMilestones = milestoneRepository.findByContractIdOrderByDueDateAscIdAsc(existing.getId());
            if (existingMilestones.isEmpty()) {
                Milestone restoredMilestone = createDefaultMilestone(
                        existing.getId(),
                        request.milestoneAmount(),
                        request.durationDays()
                );
                existingMilestones = List.of(restoredMilestone);
            }
            return toContractResponse(existing, existingMilestones);
        }

        Contract contract = new Contract();
        contract.setSourceProposalId(request.proposalId());
        contract.setJobId(request.jobId());
        contract.setClientId(request.clientId());
        contract.setFreelancerId(request.freelancerId());
        contract.setStatus(ContractStatus.ACTIVE);

        Contract savedContract;
        try {
            savedContract = contractRepository.save(contract);
        } catch (DataIntegrityViolationException ex) {
            Contract alreadyCreated = contractRepository.findBySourceProposalId(request.proposalId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Contract already exists for this proposal"));
            return toContractResponse(alreadyCreated, milestoneRepository.findByContractIdOrderByDueDateAscIdAsc(alreadyCreated.getId()));
        }

        Milestone savedMilestone = createDefaultMilestone(
                savedContract.getId(),
                request.milestoneAmount(),
                request.durationDays()
        );

        return toContractResponse(savedContract, List.of(savedMilestone));
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> getMyContracts(JwtUserPrincipal principal) {
        Long userId = requireUserId(principal);
        String role = normalizeRole(principal);
        List<Contract> contracts;
        if ("CLIENT".equals(role)) {
            contracts = contractRepository.findByClientIdOrderByCreatedAtDesc(userId);
        } else if ("FREELANCER".equals(role)) {
            contracts = contractRepository.findByFreelancerIdOrderByCreatedAtDesc(userId);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role for contracts");
        }

        if (contracts.isEmpty()) {
            return List.of();
        }

        List<Long> contractIds = contracts.stream().map(Contract::getId).toList();
        List<Milestone> milestones = milestoneRepository.findByContractIdInOrderByContractIdAscDueDateAscIdAsc(contractIds);
        Map<Long, List<Milestone>> milestonesByContractId = new LinkedHashMap<>();
        for (Milestone milestone : milestones) {
            milestonesByContractId.computeIfAbsent(milestone.getContractId(), ignored -> new ArrayList<>()).add(milestone);
        }

        return contracts.stream()
                .map(contract -> toContractResponse(
                        contract,
                        milestonesByContractId.getOrDefault(contract.getId(), Collections.emptyList())
                ))
                .toList();
    }

    @Transactional
    public ContractResponse addMilestone(Long contractId, CreateMilestoneRequest request, JwtUserPrincipal principal) {
        ensureClientRole(principal);
        Long userId = requireUserId(principal);
        validateCreateMilestoneRequest(request);

        Contract contract = findContract(contractId);
        if (!contract.getClientId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only contract owner can add milestone");
        }
        if (contract.getStatus() == ContractStatus.COMPLETED || contract.getStatus() == ContractStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add milestone to closed contract");
        }

        Milestone milestone = new Milestone();
        milestone.setContractId(contract.getId());
        milestone.setTitle(normalizeRequiredText(request.title(), "title"));
        milestone.setAmount(request.amount());
        milestone.setDueDate(request.dueDate());
        milestone.setStatus(MilestoneStatus.PENDING);
        milestoneRepository.save(milestone);

        if (contract.getStatus() == ContractStatus.CREATED) {
            contract.setStatus(ContractStatus.ACTIVE);
            contractRepository.save(contract);
        }

        List<Milestone> milestones = milestoneRepository.findByContractIdOrderByDueDateAscIdAsc(contract.getId());
        return toContractResponse(contract, milestones);
    }

    @Transactional
    public MilestoneResponse completeMilestone(Long milestoneId, JwtUserPrincipal principal) {
        Long userId = requireUserId(principal);
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found"));
        Contract contract = findContract(milestone.getContractId());

        boolean isParticipant = contract.getClientId().equals(userId)
                || contract.getFreelancerId().equals(userId);
        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only contract participants can complete milestone");
        }

        boolean transitionedToCompleted = false;
        if (milestone.getStatus() != MilestoneStatus.COMPLETED) {
            milestone.setStatus(MilestoneStatus.COMPLETED);
            milestone.setCompletedAt(Instant.now());
            milestone = milestoneRepository.save(milestone);
            transitionedToCompleted = true;
        }

        long remaining = milestoneRepository.countByContractIdAndStatusNot(contract.getId(), MilestoneStatus.COMPLETED);
        if (remaining == 0 && contract.getStatus() != ContractStatus.COMPLETED) {
            contract.setStatus(ContractStatus.COMPLETED);
            contract.setCompletedAt(Instant.now());
            contractRepository.save(contract);
        }

        if (transitionedToCompleted) {
            milestoneEventPublisher.publishMilestoneCompleted(milestone, contract);
        }

        return toMilestoneResponse(milestone);
    }

    private Contract findContract(Long contractId) {
        if (contractId == null || contractId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contractId must be greater than 0");
        }
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
    }

    private void ensureClientRole(JwtUserPrincipal principal) {
        String role = normalizeRole(principal);
        if (!"CLIENT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CLIENT can perform this action");
        }
    }

    private String normalizeRole(JwtUserPrincipal principal) {
        if (principal == null || principal.role() == null || principal.role().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid role in token");
        }
        return principal.role().trim().toUpperCase(Locale.ROOT);
    }

    private Long requireUserId(JwtUserPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user in token");
        }
        return principal.userId();
    }

    private void validateCreateContractRequest(CreateContractFromProposalRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.proposalId() == null || request.proposalId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "proposalId must be greater than 0");
        }
        if (request.jobId() == null || request.jobId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobId must be greater than 0");
        }
        if (request.clientId() == null || request.clientId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId must be greater than 0");
        }
        if (request.freelancerId() == null || request.freelancerId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "freelancerId must be greater than 0");
        }
        if (request.milestoneAmount() == null || request.milestoneAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "milestoneAmount must be greater than 0");
        }
        if (request.durationDays() == null || request.durationDays() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationDays must be greater than 0");
        }
    }

    private void validateCreateMilestoneRequest(CreateMilestoneRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than 0");
        }
        if (request.dueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueDate is required");
        }
        if (request.dueDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueDate must be in the present or future");
        }
    }

    private String normalizeRequiredText(String text, String field) {
        if (text == null || text.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return text.trim();
    }

    private Milestone createDefaultMilestone(Long contractId, java.math.BigDecimal amount, Integer durationDays) {
        Milestone defaultMilestone = new Milestone();
        defaultMilestone.setContractId(contractId);
        defaultMilestone.setTitle("Default milestone");
        defaultMilestone.setAmount(amount);
        try {
            defaultMilestone.setDueDate(LocalDate.now().plusDays(durationDays.longValue()));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid durationDays for milestone due date");
        }
        defaultMilestone.setStatus(MilestoneStatus.PENDING);
        return milestoneRepository.save(defaultMilestone);
    }

    private ContractResponse toContractResponse(Contract contract, List<Milestone> milestones) {
        return new ContractResponse(
                contract.getId(),
                contract.getSourceProposalId(),
                contract.getJobId(),
                contract.getClientId(),
                contract.getFreelancerId(),
                contract.getStatus().name(),
                contract.getCreatedAt(),
                contract.getUpdatedAt(),
                contract.getCompletedAt(),
                milestones.stream().map(this::toMilestoneResponse).toList()
        );
    }

    private MilestoneResponse toMilestoneResponse(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getId(),
                milestone.getContractId(),
                milestone.getTitle(),
                milestone.getAmount(),
                milestone.getDueDate(),
                milestone.getStatus().name(),
                milestone.getCompletedAt(),
                milestone.getCreatedAt(),
                milestone.getUpdatedAt()
        );
    }
}

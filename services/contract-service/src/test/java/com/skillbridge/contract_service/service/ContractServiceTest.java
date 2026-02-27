package com.skillbridge.contract_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private MilestoneEventPublisher milestoneEventPublisher;

    @InjectMocks
    private ContractService contractService;

    @Test
    void createContractFromProposalShouldCreateDefaultMilestone() {
        CreateContractFromProposalRequest request = new CreateContractFromProposalRequest(
                11L,
                22L,
                33L,
                44L,
                BigDecimal.valueOf(500),
                14
        );
        when(contractRepository.findBySourceProposalId(11L)).thenReturn(Optional.empty());
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract contract = invocation.getArgument(0);
            contract.setId(777L);
            return contract;
        });
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(invocation -> {
            Milestone milestone = invocation.getArgument(0);
            milestone.setId(888L);
            return milestone;
        });

        ContractResponse response = contractService.createContractFromProposal(request);

        assertThat(response.id()).isEqualTo(777L);
        assertThat(response.sourceProposalId()).isEqualTo(11L);
        assertThat(response.jobId()).isEqualTo(22L);
        assertThat(response.clientId()).isEqualTo(33L);
        assertThat(response.freelancerId()).isEqualTo(44L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.milestones()).hasSize(1);

        MilestoneResponse milestone = response.milestones().get(0);
        assertThat(milestone.id()).isEqualTo(888L);
        assertThat(milestone.title()).isEqualTo("Default milestone");
        assertThat(milestone.amount()).isEqualByComparingTo("500");
        assertThat(milestone.status()).isEqualTo("PENDING");
        assertThat(milestone.dueDate()).isEqualTo(LocalDate.now().plusDays(14));
    }

    @Test
    void createContractFromProposalShouldRestoreDefaultMilestoneForExistingContract() {
        CreateContractFromProposalRequest request = new CreateContractFromProposalRequest(
                100L,
                200L,
                300L,
                400L,
                BigDecimal.valueOf(350),
                5
        );

        Contract existingContract = new Contract();
        existingContract.setId(900L);
        existingContract.setSourceProposalId(100L);
        existingContract.setJobId(200L);
        existingContract.setClientId(300L);
        existingContract.setFreelancerId(400L);
        existingContract.setStatus(ContractStatus.ACTIVE);

        when(contractRepository.findBySourceProposalId(100L)).thenReturn(Optional.of(existingContract));
        when(milestoneRepository.findByContractIdOrderByDueDateAscIdAsc(900L)).thenReturn(List.of());
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(invocation -> {
            Milestone milestone = invocation.getArgument(0);
            milestone.setId(901L);
            return milestone;
        });

        ContractResponse response = contractService.createContractFromProposal(request);

        assertThat(response.id()).isEqualTo(900L);
        assertThat(response.milestones()).hasSize(1);
        assertThat(response.milestones().get(0).title()).isEqualTo("Default milestone");
        verify(milestoneRepository).save(any(Milestone.class));
    }

    @Test
    void createContractFromProposalShouldValidateInput() {
        CreateContractFromProposalRequest invalidRequest = new CreateContractFromProposalRequest(
                1L,
                2L,
                3L,
                3L,
                BigDecimal.valueOf(100),
                10
        );

        assertThatThrownBy(() -> contractService.createContractFromProposal(invalidRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException error = (ResponseStatusException) ex;
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).isEqualTo("clientId and freelancerId must be different");
                });
    }

    @Test
    void addMilestoneShouldRejectPastDueDate() {
        JwtUserPrincipal client = new JwtUserPrincipal(10L, "client@example.com", "CLIENT");
        CreateMilestoneRequest request = new CreateMilestoneRequest(
                "Phase 2",
                BigDecimal.valueOf(120),
                LocalDate.now().minusDays(1)
        );

        assertThatThrownBy(() -> contractService.addMilestone(1L, request, client))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void completeMilestoneShouldFinalizeContractWhenAllMilestonesCompleted() {
        Contract contract = new Contract();
        contract.setId(77L);
        contract.setClientId(7L);
        contract.setFreelancerId(8L);
        contract.setStatus(ContractStatus.ACTIVE);
        when(contractRepository.findById(77L)).thenReturn(Optional.of(contract));

        Milestone milestone = new Milestone();
        milestone.setId(66L);
        milestone.setContractId(77L);
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setTitle("Initial");
        milestone.setAmount(BigDecimal.valueOf(150));
        milestone.setDueDate(LocalDate.now().plusDays(2));
        when(milestoneRepository.findById(66L)).thenReturn(Optional.of(milestone));
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(milestoneRepository.countByContractIdAndStatusNot(77L, MilestoneStatus.COMPLETED)).thenReturn(0L);

        JwtUserPrincipal freelancer = new JwtUserPrincipal(8L, "freelancer@example.com", "FREELANCER");
        MilestoneResponse response = contractService.completeMilestone(66L, freelancer);

        assertThat(response.id()).isEqualTo(66L);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.completedAt()).isNotNull();

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(contractCaptor.capture());
        Contract savedContract = contractCaptor.getValue();
        assertThat(savedContract.getStatus()).isEqualTo(ContractStatus.COMPLETED);
        assertThat(savedContract.getCompletedAt()).isNotNull();
        verify(milestoneEventPublisher).publishMilestoneCompleted(any(Milestone.class), any(Contract.class));
    }
}

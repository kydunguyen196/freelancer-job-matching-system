package com.skillbridge.proposal_service.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalStatus;
import com.skillbridge.proposal_service.dto.CreateProposalRequest;
import com.skillbridge.proposal_service.dto.PagedResult;
import com.skillbridge.proposal_service.dto.ProposalResponse;
import com.skillbridge.proposal_service.messaging.ProposalEventPublisher;
import com.skillbridge.proposal_service.repository.ProposalRepository;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;

@Service
public class ProposalService {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ProposalRepository proposalRepository;
    private final ProposalEventPublisher proposalEventPublisher;
    private final RestClient jobRestClient;
    private final RestClient contractRestClient;
    private final String internalApiKey;

    public ProposalService(
            ProposalRepository proposalRepository,
            ProposalEventPublisher proposalEventPublisher,
            @Value("${app.services.job-base-url:http://localhost:8083}") String jobBaseUrl,
            @Value("${app.services.contract-base-url:http://localhost:8085}") String contractBaseUrl,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.proposalRepository = proposalRepository;
        this.proposalEventPublisher = proposalEventPublisher;
        this.jobRestClient = RestClient.builder()
                .baseUrl(jobBaseUrl)
                .build();
        this.contractRestClient = RestClient.builder()
                .baseUrl(contractBaseUrl)
                .build();
        this.internalApiKey = internalApiKey;
    }

    @Transactional
    public ProposalResponse createProposal(CreateProposalRequest request, JwtUserPrincipal principal) {
        ensureRole(principal, "FREELANCER", "Only FREELANCER can apply proposals");
        JobSummary job = fetchJob(request.jobId());
        if (!"OPEN".equalsIgnoreCase(job.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot apply proposal to non-open job");
        }

        if (proposalRepository.existsByJobIdAndFreelancerId(request.jobId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already applied to this job");
        }

        Proposal proposal = new Proposal();
        proposal.setJobId(request.jobId());
        proposal.setFreelancerId(principal.userId());
        proposal.setFreelancerEmail(normalizeEmail(principal.email()));
        proposal.setCoverLetter(normalizeRequiredText(request.coverLetter(), "coverLetter"));
        proposal.setPrice(request.price());
        proposal.setDurationDays(request.durationDays());
        proposal.setStatus(ProposalStatus.PENDING);

        try {
            Proposal savedProposal = proposalRepository.save(proposal);
            proposalEventPublisher.publishProposalCreated(savedProposal, job.clientId());
            return toResponse(savedProposal);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already applied to this job");
        }
    }

    @Transactional(readOnly = true)
    public PagedResult<ProposalResponse> listProposalsByJob(Long jobId, JwtUserPrincipal principal, int page, int size) {
        ensureRole(principal, "CLIENT", "Only CLIENT can view proposals by job");
        validatePaging(page, size);
        assertJobOwner(jobId, principal.userId());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Proposal> result = proposalRepository.findByJobId(jobId, pageable);

        List<ProposalResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PagedResult<>(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional
    public ProposalResponse acceptProposal(Long proposalId, JwtUserPrincipal principal) {
        ensureRole(principal, "CLIENT", "Only CLIENT can accept proposal");

        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        assertJobOwner(proposal.getJobId(), principal.userId());

        if (proposal.getStatus() == ProposalStatus.ACCEPTED) {
            Long acceptedBy = proposal.getAcceptedByClientId() != null ? proposal.getAcceptedByClientId() : principal.userId();
            createContractForAcceptedProposal(proposal, acceptedBy);
            return toResponse(proposal);
        }
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING proposal can be accepted");
        }

        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setAcceptedByClientId(principal.userId());
        proposal.setAcceptedAt(Instant.now());
        Proposal savedProposal = proposalRepository.save(proposal);
        createContractForAcceptedProposal(savedProposal, principal.userId());
        proposalEventPublisher.publishProposalAccepted(savedProposal, principal.userId());

        return toResponse(savedProposal);
    }

    private void assertJobOwner(Long jobId, Long clientId) {
        JobSummary job = fetchJob(jobId);
        if (job.clientId() == null || !job.clientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only job owner can perform this action");
        }
    }

    private JobSummary fetchJob(Long jobId) {
        try {
            JobSummary job = jobRestClient.get()
                    .uri("/jobs/{id}", jobId)
                    .retrieve()
                    .body(JobSummary.class);
            if (job == null || job.id() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from job-service");
            }
            return job;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to verify job");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "job-service is unavailable");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to communicate with job-service");
        }
    }

    private void ensureRole(JwtUserPrincipal principal, String expectedRole, String errorMessage) {
        if (principal == null || principal.role() == null || !expectedRole.equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMessage);
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
    }

    private String normalizeRequiredText(String text, String field) {
        if (text == null || text.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return text.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email in token");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void createContractForAcceptedProposal(Proposal proposal, Long clientId) {
        CreateContractFromProposalRequest requestBody = new CreateContractFromProposalRequest(
                proposal.getId(),
                proposal.getJobId(),
                clientId,
                proposal.getFreelancerId(),
                proposal.getPrice(),
                proposal.getDurationDays()
        );

        try {
            contractRestClient.post()
                    .uri("/contracts/internal/from-proposal")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create contract after proposal acceptance");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "contract-service is unavailable");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to communicate with contract-service");
        }
    }

    private ProposalResponse toResponse(Proposal proposal) {
        return new ProposalResponse(
                proposal.getId(),
                proposal.getJobId(),
                proposal.getFreelancerId(),
                proposal.getFreelancerEmail(),
                proposal.getCoverLetter(),
                proposal.getPrice(),
                proposal.getDurationDays(),
                proposal.getStatus().name(),
                proposal.getAcceptedByClientId(),
                proposal.getAcceptedAt(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt()
        );
    }

    private record JobSummary(
            Long id,
            Long clientId,
            String status
    ) {
    }

    private record CreateContractFromProposalRequest(
            Long proposalId,
            Long jobId,
            Long clientId,
            Long freelancerId,
            java.math.BigDecimal milestoneAmount,
            Integer durationDays
    ) {
    }
}

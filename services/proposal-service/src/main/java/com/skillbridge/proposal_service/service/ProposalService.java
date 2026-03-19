package com.skillbridge.proposal_service.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.skillbridge.proposal_service.dto.JobProposalStatsResponse;
import com.skillbridge.proposal_service.dto.PagedResult;
import com.skillbridge.proposal_service.dto.ProposalDashboardResponse;
import com.skillbridge.proposal_service.dto.ProposalResponse;
import com.skillbridge.proposal_service.dto.RejectProposalRequest;
import com.skillbridge.proposal_service.dto.ReviewProposalRequest;
import com.skillbridge.proposal_service.dto.ScheduleInterviewRequest;
import com.skillbridge.proposal_service.messaging.ProposalEventPublisher;
import com.skillbridge.proposal_service.repository.ProposalRepository;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;

@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final ProposalRepository proposalRepository;
    private final ProposalEventPublisher proposalEventPublisher;
    private final RestClient jobRestClient;
    private final RestClient contractRestClient;
    private final RestClient notificationRestClient;
    private final CalendarService calendarService;
    private final String internalApiKey;

    public ProposalService(
            ProposalRepository proposalRepository,
            ProposalEventPublisher proposalEventPublisher,
            CalendarService calendarService,
            @Value("${app.services.job-base-url:http://localhost:8083}") String jobBaseUrl,
            @Value("${app.services.contract-base-url:http://localhost:8085}") String contractBaseUrl,
            @Value("${app.services.notification-base-url:http://localhost:8086}") String notificationBaseUrl,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.proposalRepository = proposalRepository;
        this.proposalEventPublisher = proposalEventPublisher;
        this.calendarService = calendarService;
        this.jobRestClient = RestClient.builder().baseUrl(jobBaseUrl).build();
        this.contractRestClient = RestClient.builder().baseUrl(contractBaseUrl).build();
        this.notificationRestClient = RestClient.builder().baseUrl(notificationBaseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    @Transactional
    public ProposalResponse createProposal(CreateProposalRequest request, JwtUserPrincipal principal) {
        ensureRole(principal, "FREELANCER", "Only FREELANCER can apply proposals");
        JobSummary job = fetchJob(request.jobId());
        if (job.status() != ProposalJobStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot apply proposal to non-open job");
        }

        if (proposalRepository.existsByJobIdAndFreelancerId(request.jobId(), principal.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already applied to this job");
        }

        Proposal proposal = new Proposal();
        proposal.setJobId(request.jobId());
        proposal.setClientId(job.clientId());
        proposal.setFreelancerId(principal.userId());
        proposal.setFreelancerEmail(normalizeEmail(principal.email()));
        proposal.setCoverLetter(normalizeRequiredText(request.coverLetter(), "coverLetter", 4000));
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
    public PagedResult<ProposalResponse> listProposalsByJob(
            Long jobId,
            ProposalStatus status,
            JwtUserPrincipal principal,
            int page,
            int size
    ) {
        ensureRole(principal, "CLIENT", "Only CLIENT can view proposals by job");
        validatePaging(page, size);
        assertJobOwner(jobId, principal.userId());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Proposal> result = status == null
                ? proposalRepository.findByJobId(jobId, pageable)
                : proposalRepository.findByJobIdAndStatus(jobId, status, pageable);
        return toPagedResult(result);
    }

    @Transactional(readOnly = true)
    public PagedResult<ProposalResponse> listMyProposals(
            ProposalStatus status,
            JwtUserPrincipal principal,
            int page,
            int size
    ) {
        ensureRole(principal, "FREELANCER", "Only FREELANCER can view their proposals");
        validatePaging(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Proposal> result = status == null
                ? proposalRepository.findByFreelancerId(principal.userId(), pageable)
                : proposalRepository.findByFreelancerIdAndStatus(principal.userId(), status, pageable);
        return toPagedResult(result);
    }

    @Transactional(readOnly = true)
    public ProposalDashboardResponse getMyDashboard(JwtUserPrincipal principal) {
        if (isRole(principal, "CLIENT")) {
            List<Proposal> proposals = listClientOwnedProposals(principal.userId());
            return toDashboard(proposals);
        }
        if (isRole(principal, "FREELANCER")) {
            List<Proposal> proposals = proposalRepository.findByFreelancerId(principal.userId());
            return toDashboard(proposals);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role for dashboard");
    }

    @Transactional(readOnly = true)
    public ProposalResponse getProposalById(Long proposalId, JwtUserPrincipal principal) {
        Proposal proposal = findProposal(proposalId);
        assertProposalAccess(proposal, principal);
        return toResponse(proposal);
    }

    @Transactional
    public ProposalResponse reviewProposal(Long proposalId, ReviewProposalRequest request, JwtUserPrincipal principal) {
        ensureRole(principal, "CLIENT", "Only CLIENT can review proposal");
        Proposal proposal = findProposal(proposalId);
        assertProposalOwner(proposal, principal.userId());

        if (proposal.getStatus() == ProposalStatus.ACCEPTED || proposal.getStatus() == ProposalStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed proposal cannot move to REVIEWING");
        }

        Instant now = Instant.now();
        proposal.setStatus(ProposalStatus.REVIEWING);
        proposal.setReviewedByClientId(principal.userId());
        proposal.setReviewedAt(now);
        proposal.setFeedbackMessage(normalizeRequiredText(request.feedbackMessage(), "feedbackMessage", 2000));
        Proposal saved = proposalRepository.save(proposal);
        safeCreateNotification(
                saved.getFreelancerId(),
                null,
                "APPLICATION_FEEDBACK",
                "Application under review",
                "Your application for job #" + saved.getJobId() + " is now under review."
        );
        return toResponse(saved);
    }

    @Transactional
    public ProposalResponse scheduleInterview(Long proposalId, ScheduleInterviewRequest request, JwtUserPrincipal principal) {
        ensureRole(principal, "CLIENT", "Only CLIENT can schedule interviews");
        Proposal proposal = findProposal(proposalId);
        assertProposalOwner(proposal, principal.userId());

        if (proposal.getStatus() == ProposalStatus.ACCEPTED || proposal.getStatus() == ProposalStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed proposal cannot be scheduled for interview");
        }
        if (!request.interviewEndsAt().isAfter(request.interviewScheduledAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interviewEndsAt must be after interviewScheduledAt");
        }

        Instant now = Instant.now();
        proposal.setStatus(ProposalStatus.INTERVIEW_SCHEDULED);
        proposal.setReviewedByClientId(principal.userId());
        proposal.setReviewedAt(now);
        proposal.setInterviewScheduledAt(request.interviewScheduledAt());
        proposal.setInterviewEndsAt(request.interviewEndsAt());
        proposal.setInterviewMeetingLink(normalizeOptionalText(request.meetingLink(), 512));
        proposal.setInterviewNotes(normalizeOptionalText(request.notes(), 2000));
        Proposal saved = proposalRepository.save(proposal);
        String calendarWarning = attachGoogleCalendarEvent(saved, principal);

        safeCreateNotification(
                saved.getFreelancerId(),
                saved.getFreelancerEmail(),
                "INTERVIEW_SCHEDULED",
                "Interview scheduled",
                buildInterviewMessage(saved)
        );
        return toResponse(saved, calendarWarning);
    }

    @Transactional
    public ProposalResponse rejectProposal(Long proposalId, RejectProposalRequest request, JwtUserPrincipal principal) {
        ensureRole(principal, "CLIENT", "Only CLIENT can reject proposal");
        Proposal proposal = findProposal(proposalId);
        assertProposalOwner(proposal, principal.userId());

        if (proposal.getStatus() == ProposalStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted proposal cannot be rejected");
        }

        Instant now = Instant.now();
        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setReviewedByClientId(principal.userId());
        proposal.setReviewedAt(now);
        proposal.setRejectedByClientId(principal.userId());
        proposal.setRejectedAt(now);
        proposal.setFeedbackMessage(normalizeRequiredText(request.feedbackMessage(), "feedbackMessage", 2000));
        Proposal saved = proposalRepository.save(proposal);

        safeCreateNotification(
                saved.getFreelancerId(),
                saved.getFreelancerEmail(),
                "PROPOSAL_REJECTED",
                "Application update",
                "Your application for job #" + saved.getJobId() + " was rejected. " + saved.getFeedbackMessage()
        );
        return toResponse(saved);
    }

    @Transactional
    public ProposalResponse acceptProposal(Long proposalId, JwtUserPrincipal principal) {
        ensureRole(principal, "CLIENT", "Only CLIENT can accept proposal");

        Proposal proposal = findProposal(proposalId);
        assertProposalOwner(proposal, principal.userId());

        if (proposal.getStatus() == ProposalStatus.ACCEPTED) {
            return toResponse(proposal);
        }
        if (proposal.getStatus() == ProposalStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rejected proposal cannot be accepted");
        }

        Instant now = Instant.now();
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setReviewedByClientId(principal.userId());
        proposal.setReviewedAt(now);
        proposal.setAcceptedByClientId(principal.userId());
        proposal.setAcceptedAt(now);
        Proposal savedProposal = proposalRepository.save(proposal);

        updateJobStatusInternal(savedProposal.getJobId(), "IN_PROGRESS");
        createContractForAcceptedProposal(savedProposal, principal.userId());
        rejectCompetingProposals(savedProposal, principal.userId(), now);
        proposalEventPublisher.publishProposalAccepted(savedProposal, principal.userId());

        return toResponse(savedProposal);
    }

    private void rejectCompetingProposals(Proposal acceptedProposal, Long clientId, Instant now) {
        List<Proposal> competingProposals = proposalRepository.findByJobIdAndIdNot(acceptedProposal.getJobId(), acceptedProposal.getId());
        for (Proposal proposal : competingProposals) {
            if (proposal.getStatus() == ProposalStatus.ACCEPTED || proposal.getStatus() == ProposalStatus.REJECTED) {
                continue;
            }
            proposal.setStatus(ProposalStatus.REJECTED);
            proposal.setReviewedByClientId(clientId);
            proposal.setReviewedAt(now);
            proposal.setRejectedByClientId(clientId);
            proposal.setRejectedAt(now);
            if (proposal.getFeedbackMessage() == null || proposal.getFeedbackMessage().isBlank()) {
                proposal.setFeedbackMessage("The position has been filled by another candidate.");
            }
            proposalRepository.save(proposal);
            safeCreateNotification(
                    proposal.getFreelancerId(),
                    proposal.getFreelancerEmail(),
                    "PROPOSAL_REJECTED",
                    "Application update",
                    "Your application for job #" + proposal.getJobId() + " was closed because another candidate was selected."
            );
        }
    }

    private void assertProposalOwner(Proposal proposal, Long clientId) {
        Long proposalClientId = resolveProposalClientId(proposal);
        if (proposalClientId == null || !proposalClientId.equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only job owner can perform this action");
        }
    }

    private void assertProposalAccess(Proposal proposal, JwtUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (proposal.getFreelancerId().equals(principal.userId())) {
            return;
        }
        Long clientId = resolveProposalClientId(proposal);
        if (clientId != null && clientId.equals(principal.userId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this proposal");
    }

    private void assertJobOwner(Long jobId, Long clientId) {
        JobSummary job = fetchJob(jobId);
        if (job.clientId() == null || !job.clientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only job owner can perform this action");
        }
    }

    private Proposal findProposal(Long proposalId) {
        return proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
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

    private void updateJobStatusInternal(Long jobId, String status) {
        try {
            jobRestClient.patch()
                    .uri("/jobs/internal/{id}/status", jobId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new UpdateJobStatusRequest(status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync job status after proposal acceptance");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "job-service is unavailable");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to communicate with job-service");
        }
    }

    private void ensureRole(JwtUserPrincipal principal, String expectedRole, String errorMessage) {
        if (!isRole(principal, expectedRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMessage);
        }
    }

    private boolean isRole(JwtUserPrincipal principal, String role) {
        return principal != null && principal.role() != null && role.equalsIgnoreCase(principal.role());
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
    }

    private String normalizeRequiredText(String text, String field, int maxLength) {
        String normalized = normalizeOptionalText(text, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptionalText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text value exceeds allowed length");
        }
        return normalized;
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

    private void safeCreateNotification(Long recipientUserId, String recipientEmail, String type, String title, String message) {
        try {
            notificationRestClient.post()
                    .uri("/notifications/internal")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new CreateNotificationRequest(recipientUserId, recipientEmail, type, title, message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Failed to create notification type={} recipient={}: {}", type, recipientUserId, ex.getMessage());
        }
    }

    private String buildInterviewMessage(Proposal proposal) {
        StringBuilder builder = new StringBuilder("Interview scheduled for job #")
                .append(proposal.getJobId())
                .append(" at ")
                .append(proposal.getInterviewScheduledAt());
        if (proposal.getInterviewMeetingLink() != null) {
            builder.append(". Link: ").append(proposal.getInterviewMeetingLink());
        }
        return builder.toString();
    }

    private String attachGoogleCalendarEvent(Proposal proposal, JwtUserPrincipal principal) {
        try {
            JobSummary job = fetchJob(proposal.getJobId());
            CalendarService.CreateInterviewEventResult result = calendarService.createInterviewEvent(new CalendarService.CreateInterviewEventRequest(
                    proposal.getId(),
                    proposal.getJobId(),
                    job.title(),
                    proposal.getFreelancerId(),
                    proposal.getFreelancerEmail(),
                    principal.userId(),
                    normalizeEmail(principal.email()),
                    proposal.getPrice(),
                    proposal.getDurationDays(),
                    proposal.getCoverLetter(),
                    proposal.getInterviewScheduledAt(),
                    proposal.getInterviewEndsAt(),
                    proposal.getInterviewMeetingLink(),
                    proposal.getInterviewNotes()
            ));
            if (result.externalEventId() != null) {
                proposal.setGoogleEventId(result.externalEventId());
            }
            return result.warning();
        } catch (RuntimeException ex) {
            log.warn("Failed to attach Google Calendar event for proposalId={}", proposal.getId(), ex);
            return "Google Calendar event could not be created";
        }
    }

    private PagedResult<ProposalResponse> toPagedResult(Page<Proposal> result) {
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

    private ProposalDashboardResponse toDashboard(List<Proposal> proposals) {
        List<JobProposalStatsResponse> jobStats = proposals.stream()
                .collect(java.util.stream.Collectors.groupingBy(Proposal::getJobId))
                .entrySet()
                .stream()
                .map(entry -> toJobStats(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> Long.compare(right.totalApplications(), left.totalApplications()))
                .toList();

        return new ProposalDashboardResponse(
                proposals.size(),
                countByStatus(proposals, ProposalStatus.PENDING),
                countByStatus(proposals, ProposalStatus.REVIEWING),
                countByStatus(proposals, ProposalStatus.INTERVIEW_SCHEDULED),
                countByStatus(proposals, ProposalStatus.ACCEPTED),
                countByStatus(proposals, ProposalStatus.REJECTED),
                jobStats
        );
    }

    private JobProposalStatsResponse toJobStats(Long jobId, List<Proposal> proposals) {
        return new JobProposalStatsResponse(
                jobId,
                proposals.size(),
                countByStatus(proposals, ProposalStatus.PENDING),
                countByStatus(proposals, ProposalStatus.REVIEWING),
                countByStatus(proposals, ProposalStatus.INTERVIEW_SCHEDULED),
                countByStatus(proposals, ProposalStatus.ACCEPTED),
                countByStatus(proposals, ProposalStatus.REJECTED)
        );
    }

    private long countByStatus(List<Proposal> proposals, ProposalStatus status) {
        return proposals.stream().filter(proposal -> proposal.getStatus() == status).count();
    }

    private List<Proposal> listClientOwnedProposals(Long clientId) {
        Map<Long, Long> jobOwnerCache = new HashMap<>();
        return proposalRepository.findAll().stream()
                .filter(proposal -> clientId.equals(resolveProposalClientId(proposal, jobOwnerCache)))
                .toList();
    }

    private Long resolveProposalClientId(Proposal proposal) {
        return resolveProposalClientId(proposal, new HashMap<>());
    }

    private Long resolveProposalClientId(Proposal proposal, Map<Long, Long> jobOwnerCache) {
        if (proposal.getClientId() != null) {
            return proposal.getClientId();
        }
        return jobOwnerCache.computeIfAbsent(proposal.getJobId(), jobId -> {
            JobSummary job = fetchJob(jobId);
            return job.clientId();
        });
    }

    private ProposalResponse toResponse(Proposal proposal) {
        return toResponse(proposal, null);
    }

    private ProposalResponse toResponse(Proposal proposal, String calendarWarning) {
        return new ProposalResponse(
                proposal.getId(),
                proposal.getJobId(),
                proposal.getClientId(),
                proposal.getFreelancerId(),
                proposal.getFreelancerEmail(),
                proposal.getCoverLetter(),
                proposal.getPrice(),
                proposal.getDurationDays(),
                proposal.getStatus().name(),
                proposal.getReviewedByClientId(),
                proposal.getReviewedAt(),
                proposal.getRejectedByClientId(),
                proposal.getRejectedAt(),
                proposal.getFeedbackMessage(),
                proposal.getInterviewScheduledAt(),
                proposal.getInterviewEndsAt(),
                proposal.getInterviewMeetingLink(),
                proposal.getInterviewNotes(),
                proposal.getGoogleEventId(),
                calendarWarning,
                proposal.getAcceptedByClientId(),
                proposal.getAcceptedAt(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt()
        );
    }

    private record JobSummary(
            Long id,
            Long clientId,
            String title,
            ProposalJobStatus status
    ) {
    }

    private enum ProposalJobStatus {
        DRAFT,
        OPEN,
        IN_PROGRESS,
        CLOSED,
        EXPIRED
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

    private record UpdateJobStatusRequest(
            String status
    ) {
    }

    private record CreateNotificationRequest(
            Long recipientUserId,
            String recipientEmail,
            String type,
            String title,
            String message
    ) {
    }
}

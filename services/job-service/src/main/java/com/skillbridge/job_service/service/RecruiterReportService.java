package com.skillbridge.job_service.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.job_service.domain.Job;
import com.skillbridge.job_service.domain.JobStatus;
import com.skillbridge.job_service.dto.RecruiterReportConversionResponse;
import com.skillbridge.job_service.dto.RecruiterReportOverviewResponse;
import com.skillbridge.job_service.dto.RecruiterReportSeriesPointResponse;
import com.skillbridge.job_service.dto.RecruiterReportSeriesResponse;
import com.skillbridge.job_service.dto.RecruiterTopJobPerformanceResponse;
import com.skillbridge.job_service.repository.JobRepository;
import com.skillbridge.job_service.security.JwtUserPrincipal;

@Service
public class RecruiterReportService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterReportService.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final JobRepository jobRepository;
    private final RestClient proposalRestClient;
    private final RestClient contractRestClient;
    private final String internalApiKey;

    public RecruiterReportService(
            JobRepository jobRepository,
            @Value("${app.services.proposal-base-url:http://localhost:8084}") String proposalBaseUrl,
            @Value("${app.services.contract-base-url:http://localhost:8085}") String contractBaseUrl,
            @Value("${app.internal.api-key}") String internalApiKey
    ) {
        this.jobRepository = jobRepository;
        this.proposalRestClient = RestClient.builder().baseUrl(proposalBaseUrl).build();
        this.contractRestClient = RestClient.builder().baseUrl(contractBaseUrl).build();
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey;
    }

    @Transactional(readOnly = true)
    public RecruiterReportOverviewResponse getOverview(
            JwtUserPrincipal principal,
            Instant from,
            Instant to
    ) {
        Long clientId = requireClientPrincipal(principal);
        Range range = normalizeRange(from, to);
        List<String> warnings = new ArrayList<>();

        List<Job> jobs = jobRepository.findByClientIdOrderByUpdatedAtDesc(clientId);
        long totalJobs = jobs.size();
        long openJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.OPEN).count();
        long closedJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.CLOSED).count();
        long expiredJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.EXPIRED).count();
        long jobsCreatedInRange = jobs.stream().filter(job -> inRange(job.getCreatedAt(), range)).count();

        InternalProposalSummaryResponse proposalSummary = fetchProposalSummary(clientId, range, warnings);
        InternalContractSummaryResponse contractSummary = fetchContractSummary(clientId, range, warnings);

        return new RecruiterReportOverviewResponse(
                totalJobs,
                openJobs,
                closedJobs,
                expiredJobs,
                jobsCreatedInRange,
                proposalSummary.totalProposals(),
                proposalSummary.pendingProposals(),
                proposalSummary.reviewingProposals(),
                proposalSummary.interviewsScheduled(),
                proposalSummary.acceptedProposals(),
                proposalSummary.rejectedProposals(),
                contractSummary.totalContracts(),
                contractSummary.activeContracts(),
                contractSummary.completedContracts(),
                contractSummary.cancelledContracts(),
                List.copyOf(warnings)
        );
    }

    @Transactional(readOnly = true)
    public RecruiterReportSeriesResponse getSeries(
            JwtUserPrincipal principal,
            Instant from,
            Instant to,
            ReportGroupBy groupBy,
            String timezone
    ) {
        Long clientId = requireClientPrincipal(principal);
        Range range = normalizeRange(from, to);
        ReportGroupBy safeGroupBy = groupBy == null ? ReportGroupBy.DAY : groupBy;
        ZoneId zoneId = resolveTimezone(timezone);
        List<String> warnings = new ArrayList<>();

        Map<Instant, MutableSeriesCounter> counters = new LinkedHashMap<>();
        Instant cursor = toBucketStart(range.from(), safeGroupBy, zoneId);
        while (!cursor.isAfter(range.to())) {
            counters.put(cursor, new MutableSeriesCounter());
            cursor = nextBucketStart(cursor, safeGroupBy, zoneId);
        }

        List<Job> jobs = jobRepository.findByClientIdOrderByUpdatedAtDesc(clientId);
        for (Job job : jobs) {
            if (!inRange(job.getCreatedAt(), range)) {
                continue;
            }
            Instant bucket = toBucketStart(job.getCreatedAt(), safeGroupBy, zoneId);
            counters.computeIfAbsent(bucket, ignored -> new MutableSeriesCounter()).jobsCreated++;
        }

        InternalProposalSeriesResponse proposalSeries = fetchProposalSeries(clientId, range, safeGroupBy, zoneId, warnings);
        for (InternalProposalSeriesPointResponse point : proposalSeries.points()) {
            MutableSeriesCounter counter = counters.computeIfAbsent(point.bucketStart(), ignored -> new MutableSeriesCounter());
            counter.proposals += point.proposals();
            counter.interviews += point.interviews();
            counter.accepted += point.accepted();
            counter.rejected += point.rejected();
        }

        InternalContractSeriesResponse contractSeries = fetchContractSeries(clientId, range, safeGroupBy, zoneId, warnings);
        for (InternalContractSeriesPointResponse point : contractSeries.points()) {
            MutableSeriesCounter counter = counters.computeIfAbsent(point.bucketStart(), ignored -> new MutableSeriesCounter());
            counter.hires += point.contractsCreated();
        }

        List<RecruiterReportSeriesPointResponse> points = counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RecruiterReportSeriesPointResponse(
                        entry.getKey(),
                        entry.getValue().jobsCreated,
                        entry.getValue().proposals,
                        entry.getValue().interviews,
                        entry.getValue().accepted,
                        entry.getValue().rejected,
                        entry.getValue().hires
                ))
                .toList();

        return new RecruiterReportSeriesResponse(
                range.from(),
                range.to(),
                safeGroupBy.name(),
                zoneId.getId(),
                points,
                List.copyOf(warnings)
        );
    }

    @Transactional(readOnly = true)
    public RecruiterReportConversionResponse getConversion(
            JwtUserPrincipal principal,
            Instant from,
            Instant to
    ) {
        Long clientId = requireClientPrincipal(principal);
        Range range = normalizeRange(from, to);
        List<String> warnings = new ArrayList<>();

        long jobsCreated = jobRepository.findByClientIdOrderByUpdatedAtDesc(clientId).stream()
                .filter(job -> inRange(job.getCreatedAt(), range))
                .count();
        InternalProposalSummaryResponse proposalSummary = fetchProposalSummary(clientId, range, warnings);
        InternalContractSummaryResponse contractSummary = fetchContractSummary(clientId, range, warnings);

        long proposals = proposalSummary.totalProposals();
        long interviews = proposalSummary.interviewsScheduled();
        long hires = contractSummary.totalContracts();

        return new RecruiterReportConversionResponse(
                jobsCreated,
                proposals,
                interviews,
                hires,
                percentage(proposals, jobsCreated),
                percentage(interviews, proposals),
                percentage(hires, interviews),
                percentage(hires, proposals),
                List.copyOf(warnings),
                "views->applies conversion is unavailable because view tracking events are not stored yet"
        );
    }

    @Transactional(readOnly = true)
    public List<RecruiterTopJobPerformanceResponse> getTopJobs(
            JwtUserPrincipal principal,
            Instant from,
            Instant to,
            int limit
    ) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
        }
        Long clientId = requireClientPrincipal(principal);
        Range range = normalizeRange(from, to);
        List<String> warnings = new ArrayList<>();

        List<InternalTopJobProposalPerformanceResponse> proposalTopJobs = fetchProposalTopJobs(clientId, range, limit, warnings);
        List<Long> jobIds = proposalTopJobs.stream().map(InternalTopJobProposalPerformanceResponse::jobId).toList();
        Map<Long, Job> jobsById = jobRepository.findAllById(jobIds).stream()
                .collect(java.util.stream.Collectors.toMap(Job::getId, job -> job));

        return proposalTopJobs.stream()
                .map(item -> {
                    Job job = jobsById.get(item.jobId());
                    return new RecruiterTopJobPerformanceResponse(
                            item.jobId(),
                            job == null ? "Job #" + item.jobId() : job.getTitle(),
                            job == null || job.getStatus() == null ? null : job.getStatus().name(),
                            job == null ? null : job.getBudgetMin(),
                            job == null ? null : job.getBudgetMax(),
                            job == null ? null : job.getCreatedAt(),
                            item.totalProposals(),
                            item.pendingProposals(),
                            item.reviewingProposals(),
                            item.interviewsScheduled(),
                            item.acceptedProposals(),
                            item.rejectedProposals(),
                            item.acceptanceRate()
                    );
                })
                .toList();
    }

    private InternalProposalSummaryResponse fetchProposalSummary(Long clientId, Range range, List<String> warnings) {
        try {
            InternalProposalSummaryResponse response = proposalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/proposals/internal/analytics/client/{clientId}/summary")
                            .queryParam("from", range.from())
                            .queryParam("to", range.to())
                            .build(clientId))
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalProposalSummaryResponse.class);
            return response == null ? InternalProposalSummaryResponse.empty() : response;
        } catch (RestClientException ex) {
            warnings.add("proposal-service summary unavailable");
            log.warn("Failed to fetch proposal summary for clientId={}: {}", clientId, ex.getMessage());
            return InternalProposalSummaryResponse.empty();
        }
    }

    private InternalProposalSeriesResponse fetchProposalSeries(
            Long clientId,
            Range range,
            ReportGroupBy groupBy,
            ZoneId zoneId,
            List<String> warnings
    ) {
        try {
            InternalProposalSeriesResponse response = proposalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/proposals/internal/analytics/client/{clientId}/series")
                            .queryParam("from", range.from())
                            .queryParam("to", range.to())
                            .queryParam("groupBy", groupBy.name())
                            .queryParam("timezone", zoneId.getId())
                            .build(clientId))
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalProposalSeriesResponse.class);
            return response == null ? InternalProposalSeriesResponse.empty() : response;
        } catch (RestClientException ex) {
            warnings.add("proposal-service series unavailable");
            log.warn("Failed to fetch proposal series for clientId={}: {}", clientId, ex.getMessage());
            return InternalProposalSeriesResponse.empty();
        }
    }

    private List<InternalTopJobProposalPerformanceResponse> fetchProposalTopJobs(
            Long clientId,
            Range range,
            int limit,
            List<String> warnings
    ) {
        try {
            List<InternalTopJobProposalPerformanceResponse> response = proposalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/proposals/internal/analytics/client/{clientId}/top-jobs")
                            .queryParam("from", range.from())
                            .queryParam("to", range.to())
                            .queryParam("limit", limit)
                            .build(clientId))
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<InternalTopJobProposalPerformanceResponse>>() {
                    });
            return response == null ? List.of() : response;
        } catch (RestClientException ex) {
            warnings.add("proposal-service top jobs unavailable");
            log.warn("Failed to fetch proposal top-jobs for clientId={}: {}", clientId, ex.getMessage());
            return List.of();
        }
    }

    private InternalContractSummaryResponse fetchContractSummary(Long clientId, Range range, List<String> warnings) {
        try {
            InternalContractSummaryResponse response = contractRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/contracts/internal/analytics/client/{clientId}/summary")
                            .queryParam("from", range.from())
                            .queryParam("to", range.to())
                            .build(clientId))
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalContractSummaryResponse.class);
            return response == null ? InternalContractSummaryResponse.empty() : response;
        } catch (RestClientException ex) {
            warnings.add("contract-service summary unavailable");
            log.warn("Failed to fetch contract summary for clientId={}: {}", clientId, ex.getMessage());
            return InternalContractSummaryResponse.empty();
        }
    }

    private InternalContractSeriesResponse fetchContractSeries(
            Long clientId,
            Range range,
            ReportGroupBy groupBy,
            ZoneId zoneId,
            List<String> warnings
    ) {
        try {
            InternalContractSeriesResponse response = contractRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/contracts/internal/analytics/client/{clientId}/series")
                            .queryParam("from", range.from())
                            .queryParam("to", range.to())
                            .queryParam("groupBy", groupBy.name())
                            .queryParam("timezone", zoneId.getId())
                            .build(clientId))
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalContractSeriesResponse.class);
            return response == null ? InternalContractSeriesResponse.empty() : response;
        } catch (RestClientException ex) {
            warnings.add("contract-service series unavailable");
            log.warn("Failed to fetch contract series for clientId={}: {}", clientId, ex.getMessage());
            return InternalContractSeriesResponse.empty();
        }
    }

    private Long requireClientPrincipal(JwtUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (principal.role() == null || !"CLIENT".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CLIENT can access recruiter reports");
        }
        return principal.userId();
    }

    private Range normalizeRange(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant normalizedTo = to == null ? now : to;
        Instant normalizedFrom = from == null ? normalizedTo.minusSeconds(30L * 24 * 60 * 60) : from;
        if (normalizedFrom.isAfter(normalizedTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        return new Range(normalizedFrom, normalizedTo);
    }

    private boolean inRange(Instant value, Range range) {
        if (value == null) {
            return false;
        }
        return !value.isBefore(range.from()) && !value.isAfter(range.to());
    }

    private ZoneId resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported timezone");
        }
    }

    private Instant toBucketStart(Instant timestamp, ReportGroupBy groupBy, ZoneId zoneId) {
        ZonedDateTime zoned = timestamp.atZone(zoneId);
        return switch (groupBy) {
            case DAY -> zoned.toLocalDate().atStartOfDay(zoneId).toInstant();
            case WEEK -> zoned.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId)
                    .toInstant();
            case MONTH -> LocalDate.of(zoned.getYear(), zoned.getMonth(), 1)
                    .atStartOfDay(zoneId)
                    .toInstant();
        };
    }

    private Instant nextBucketStart(Instant currentBucketStart, ReportGroupBy groupBy, ZoneId zoneId) {
        ZonedDateTime zoned = currentBucketStart.atZone(zoneId);
        return switch (groupBy) {
            case DAY -> zoned.plusDays(1).toLocalDate().atStartOfDay(zoneId).toInstant();
            case WEEK -> zoned.plusWeeks(1).toLocalDate().atStartOfDay(zoneId).toInstant();
            case MONTH -> zoned.plusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId).toInstant();
        };
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return 0d;
        }
        return Math.round(((double) numerator * 10000d) / denominator) / 100d;
    }

    private static class MutableSeriesCounter {
        long jobsCreated;
        long proposals;
        long interviews;
        long accepted;
        long rejected;
        long hires;
    }

    private record Range(Instant from, Instant to) {
    }

    private record InternalProposalSummaryResponse(
            long totalProposals,
            long pendingProposals,
            long reviewingProposals,
            long interviewsScheduled,
            long acceptedProposals,
            long rejectedProposals,
            long hiresEstimated
    ) {
        static InternalProposalSummaryResponse empty() {
            return new InternalProposalSummaryResponse(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record InternalProposalSeriesResponse(
            Instant from,
            Instant to,
            String groupBy,
            String timezone,
            List<InternalProposalSeriesPointResponse> points
    ) {
        static InternalProposalSeriesResponse empty() {
            return new InternalProposalSeriesResponse(null, null, null, null, List.of());
        }
    }

    private record InternalProposalSeriesPointResponse(
            Instant bucketStart,
            long proposals,
            long interviews,
            long accepted,
            long rejected
    ) {
    }

    private record InternalTopJobProposalPerformanceResponse(
            Long jobId,
            long totalProposals,
            long pendingProposals,
            long reviewingProposals,
            long interviewsScheduled,
            long acceptedProposals,
            long rejectedProposals,
            double acceptanceRate
    ) {
    }

    private record InternalContractSummaryResponse(
            long totalContracts,
            long activeContracts,
            long completedContracts,
            long cancelledContracts
    ) {
        static InternalContractSummaryResponse empty() {
            return new InternalContractSummaryResponse(0, 0, 0, 0);
        }
    }

    private record InternalContractSeriesResponse(
            Instant from,
            Instant to,
            String groupBy,
            String timezone,
            List<InternalContractSeriesPointResponse> points
    ) {
        static InternalContractSeriesResponse empty() {
            return new InternalContractSeriesResponse(null, null, null, null, List.of());
        }
    }

    private record InternalContractSeriesPointResponse(
            Instant bucketStart,
            long contractsCreated,
            long contractsCompleted
    ) {
    }
}

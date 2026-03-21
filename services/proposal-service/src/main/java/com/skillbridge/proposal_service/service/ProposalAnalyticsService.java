package com.skillbridge.proposal_service.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalStatus;
import com.skillbridge.proposal_service.dto.InternalProposalSeriesPointResponse;
import com.skillbridge.proposal_service.dto.InternalProposalSeriesResponse;
import com.skillbridge.proposal_service.dto.InternalProposalSummaryResponse;
import com.skillbridge.proposal_service.dto.InternalTopJobProposalPerformanceResponse;
import com.skillbridge.proposal_service.repository.ProposalRepository;

@Service
public class ProposalAnalyticsService {

    private final ProposalRepository proposalRepository;

    public ProposalAnalyticsService(ProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @Transactional(readOnly = true)
    public InternalProposalSummaryResponse getClientSummary(Long clientId, Instant from, Instant to) {
        Range range = normalizeRange(from, to);
        List<Proposal> proposals = proposalRepository.findByClientId(clientId).stream()
                .filter(proposal -> inRange(proposal.getCreatedAt(), range))
                .toList();

        long total = proposals.size();
        long pending = countByStatus(proposals, ProposalStatus.PENDING);
        long reviewing = countByStatus(proposals, ProposalStatus.REVIEWING);
        long interviews = countByStatus(proposals, ProposalStatus.INTERVIEW_SCHEDULED);
        long accepted = countByStatus(proposals, ProposalStatus.ACCEPTED);
        long rejected = countByStatus(proposals, ProposalStatus.REJECTED);

        return new InternalProposalSummaryResponse(
                total,
                pending,
                reviewing,
                interviews,
                accepted,
                rejected,
                accepted
        );
    }

    @Transactional(readOnly = true)
    public InternalProposalSeriesResponse getClientSeries(
            Long clientId,
            Instant from,
            Instant to,
            AnalyticsGroupBy groupBy,
            String timezone
    ) {
        Range range = normalizeRange(from, to);
        AnalyticsGroupBy safeGroupBy = groupBy == null ? AnalyticsGroupBy.DAY : groupBy;
        ZoneId zoneId = resolveTimezone(timezone);

        Map<Instant, Counter> counters = new LinkedHashMap<>();
        List<Proposal> proposals = proposalRepository.findByClientId(clientId);

        for (Proposal proposal : proposals) {
            incrementCounter(counters, proposal.getCreatedAt(), range, safeGroupBy, zoneId, CounterMetric.PROPOSALS);
            incrementCounter(counters, proposal.getInterviewScheduledAt(), range, safeGroupBy, zoneId, CounterMetric.INTERVIEWS);
            incrementCounter(counters, proposal.getAcceptedAt(), range, safeGroupBy, zoneId, CounterMetric.ACCEPTED);
            incrementCounter(counters, proposal.getRejectedAt(), range, safeGroupBy, zoneId, CounterMetric.REJECTED);
        }

        List<InternalProposalSeriesPointResponse> points = new ArrayList<>();
        Instant bucketCursor = toBucketStart(range.from(), safeGroupBy, zoneId);
        while (!bucketCursor.isAfter(range.to())) {
            Counter counter = counters.getOrDefault(bucketCursor, new Counter());
            points.add(new InternalProposalSeriesPointResponse(
                    bucketCursor,
                    counter.proposals,
                    counter.interviews,
                    counter.accepted,
                    counter.rejected
            ));
            bucketCursor = nextBucketStart(bucketCursor, safeGroupBy, zoneId);
        }

        return new InternalProposalSeriesResponse(
                range.from(),
                range.to(),
                safeGroupBy.name(),
                zoneId.getId(),
                points
        );
    }

    @Transactional(readOnly = true)
    public List<InternalTopJobProposalPerformanceResponse> getClientTopJobs(
            Long clientId,
            Instant from,
            Instant to,
            int limit
    ) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
        }

        Range range = normalizeRange(from, to);
        Map<Long, List<Proposal>> proposalsByJob = proposalRepository.findByClientId(clientId).stream()
                .filter(proposal -> inRange(proposal.getCreatedAt(), range))
                .collect(java.util.stream.Collectors.groupingBy(Proposal::getJobId));

        return proposalsByJob.entrySet().stream()
                .map(entry -> toTopJobPerformance(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(InternalTopJobProposalPerformanceResponse::totalProposals).reversed()
                        .thenComparingLong(InternalTopJobProposalPerformanceResponse::acceptedProposals).reversed())
                .limit(limit)
                .toList();
    }

    private InternalTopJobProposalPerformanceResponse toTopJobPerformance(Long jobId, List<Proposal> proposals) {
        long total = proposals.size();
        long pending = countByStatus(proposals, ProposalStatus.PENDING);
        long reviewing = countByStatus(proposals, ProposalStatus.REVIEWING);
        long interviews = countByStatus(proposals, ProposalStatus.INTERVIEW_SCHEDULED);
        long accepted = countByStatus(proposals, ProposalStatus.ACCEPTED);
        long rejected = countByStatus(proposals, ProposalStatus.REJECTED);
        double acceptanceRate = total == 0 ? 0d : ((double) accepted * 100d) / total;

        return new InternalTopJobProposalPerformanceResponse(
                jobId,
                total,
                pending,
                reviewing,
                interviews,
                accepted,
                rejected,
                acceptanceRate
        );
    }

    private long countByStatus(List<Proposal> proposals, ProposalStatus status) {
        return proposals.stream().filter(proposal -> proposal.getStatus() == status).count();
    }

    private void incrementCounter(
            Map<Instant, Counter> counters,
            Instant timestamp,
            Range range,
            AnalyticsGroupBy groupBy,
            ZoneId zoneId,
            CounterMetric metric
    ) {
        if (!inRange(timestamp, range)) {
            return;
        }
        Instant bucket = toBucketStart(timestamp, groupBy, zoneId);
        Counter counter = counters.computeIfAbsent(bucket, ignored -> new Counter());
        switch (metric) {
            case PROPOSALS -> counter.proposals++;
            case INTERVIEWS -> counter.interviews++;
            case ACCEPTED -> counter.accepted++;
            case REJECTED -> counter.rejected++;
        }
    }

    private Instant toBucketStart(Instant timestamp, AnalyticsGroupBy groupBy, ZoneId zoneId) {
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

    private Instant nextBucketStart(Instant currentBucketStart, AnalyticsGroupBy groupBy, ZoneId zoneId) {
        ZonedDateTime zoned = currentBucketStart.atZone(zoneId);
        return switch (groupBy) {
            case DAY -> zoned.plusDays(1).toLocalDate().atStartOfDay(zoneId).toInstant();
            case WEEK -> zoned.plusWeeks(1).toLocalDate().atStartOfDay(zoneId).toInstant();
            case MONTH -> zoned.plusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId).toInstant();
        };
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

    private enum CounterMetric {
        PROPOSALS,
        INTERVIEWS,
        ACCEPTED,
        REJECTED
    }

    private static class Counter {
        long proposals;
        long interviews;
        long accepted;
        long rejected;
    }

    private record Range(Instant from, Instant to) {
    }
}

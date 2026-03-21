package com.skillbridge.contract_service.service;

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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.contract_service.domain.Contract;
import com.skillbridge.contract_service.domain.ContractStatus;
import com.skillbridge.contract_service.dto.InternalContractSeriesPointResponse;
import com.skillbridge.contract_service.dto.InternalContractSeriesResponse;
import com.skillbridge.contract_service.dto.InternalContractSummaryResponse;
import com.skillbridge.contract_service.repository.ContractRepository;

@Service
public class ContractAnalyticsService {

    private final ContractRepository contractRepository;

    public ContractAnalyticsService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Transactional(readOnly = true)
    public InternalContractSummaryResponse getClientSummary(Long clientId, Instant from, Instant to) {
        Range range = normalizeRange(from, to);
        List<Contract> contracts = contractRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(contract -> inRange(contract.getCreatedAt(), range))
                .toList();

        return new InternalContractSummaryResponse(
                contracts.size(),
                contracts.stream().filter(contract -> contract.getStatus() == ContractStatus.ACTIVE).count(),
                contracts.stream().filter(contract -> contract.getStatus() == ContractStatus.COMPLETED).count(),
                contracts.stream().filter(contract -> contract.getStatus() == ContractStatus.CANCELLED).count()
        );
    }

    @Transactional(readOnly = true)
    public InternalContractSeriesResponse getClientSeries(
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
        List<Contract> contracts = contractRepository.findByClientIdOrderByCreatedAtDesc(clientId);

        for (Contract contract : contracts) {
            increment(counters, contract.getCreatedAt(), range, safeGroupBy, zoneId, CounterMetric.CREATED);
            increment(counters, contract.getCompletedAt(), range, safeGroupBy, zoneId, CounterMetric.COMPLETED);
        }

        List<InternalContractSeriesPointResponse> points = new ArrayList<>();
        Instant bucketCursor = toBucketStart(range.from(), safeGroupBy, zoneId);
        while (!bucketCursor.isAfter(range.to())) {
            Counter counter = counters.getOrDefault(bucketCursor, new Counter());
            points.add(new InternalContractSeriesPointResponse(bucketCursor, counter.created, counter.completed));
            bucketCursor = nextBucketStart(bucketCursor, safeGroupBy, zoneId);
        }

        return new InternalContractSeriesResponse(
                range.from(),
                range.to(),
                safeGroupBy.name(),
                zoneId.getId(),
                points
        );
    }

    private void increment(
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
        if (metric == CounterMetric.CREATED) {
            counter.created++;
        } else {
            counter.completed++;
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
        CREATED,
        COMPLETED
    }

    private static class Counter {
        long created;
        long completed;
    }

    private record Range(Instant from, Instant to) {
    }
}

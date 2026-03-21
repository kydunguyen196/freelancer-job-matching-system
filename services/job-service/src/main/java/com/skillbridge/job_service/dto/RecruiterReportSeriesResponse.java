package com.skillbridge.job_service.dto;

import java.time.Instant;
import java.util.List;

public record RecruiterReportSeriesResponse(
        Instant from,
        Instant to,
        String groupBy,
        String timezone,
        List<RecruiterReportSeriesPointResponse> points,
        List<String> warnings
) {
}

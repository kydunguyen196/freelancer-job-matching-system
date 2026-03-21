package com.skillbridge.job_service.dto;

import java.time.Instant;

public record RecruiterReportSeriesPointResponse(
        Instant bucketStart,
        long jobsCreated,
        long proposals,
        long interviews,
        long accepted,
        long rejected,
        long hires
) {
}

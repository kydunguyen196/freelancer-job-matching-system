package com.skillbridge.job_service.dto;

import java.util.List;

public record RecruiterReportConversionResponse(
        long jobsCreated,
        long proposals,
        long interviews,
        long hires,
        double jobsToProposalsRate,
        double proposalsToInterviewsRate,
        double interviewsToHiresRate,
        double proposalsToHiresRate,
        List<String> warnings,
        String note
) {
}

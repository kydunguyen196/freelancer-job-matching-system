package com.skillbridge.job_service.dto;

public record JobSearchReindexResponse(
        String provider,
        int indexedCount,
        boolean indexingActive,
        String message
) {
}

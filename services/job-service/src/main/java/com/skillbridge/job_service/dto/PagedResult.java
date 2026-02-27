package com.skillbridge.job_service.dto;

import java.util.List;

public record PagedResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}

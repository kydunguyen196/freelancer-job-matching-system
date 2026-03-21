package com.skillbridge.user_service.dto;

import java.time.Instant;

public record ProfileMediaAssetResponse(
        String type,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        String downloadUrl
) {
}

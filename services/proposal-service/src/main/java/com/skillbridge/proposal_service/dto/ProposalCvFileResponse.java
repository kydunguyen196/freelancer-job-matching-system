package com.skillbridge.proposal_service.dto;

import java.time.Instant;

public record ProposalCvFileResponse(
        Long id,
        Long proposalId,
        Long ownerUserId,
        String originalFileName,
        String objectKey,
        String contentType,
        long sizeBytes,
        String storageProvider,
        String bucketName,
        Instant uploadedAt,
        String downloadUrl,
        Instant downloadUrlExpiresAt,
        boolean directDownload
) {
}

package com.skillbridge.proposal_service.service;

import java.time.Instant;

import com.skillbridge.proposal_service.domain.FileStorageProvider;

public interface FileStorageService {

    FileStorageProvider provider();

    boolean isAvailable();

    StoredFile store(StoreFileRequest request);

    StoredFileContent load(FileReference fileReference);

    AccessUrl createDownloadAccess(FileReference fileReference, long ttlMinutes);

    void delete(FileReference fileReference);

    record StoreFileRequest(
            String objectKey,
            String originalFileName,
            String contentType,
            byte[] content
    ) {
    }

    record StoredFile(
            FileStorageProvider provider,
            String bucketName,
            String objectKey,
            String contentType,
            long sizeBytes
    ) {
    }

    record FileReference(
            FileStorageProvider provider,
            String bucketName,
            String objectKey,
            String originalFileName,
            String contentType
    ) {
    }

    record StoredFileContent(
            String originalFileName,
            String contentType,
            byte[] content
    ) {
    }

    record AccessUrl(
            String url,
            Instant expiresAt,
            boolean direct
    ) {
    }
}

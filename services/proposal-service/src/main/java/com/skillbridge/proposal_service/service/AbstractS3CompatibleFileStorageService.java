package com.skillbridge.proposal_service.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skillbridge.proposal_service.domain.FileStorageProvider;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

abstract class AbstractS3CompatibleFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(AbstractS3CompatibleFileStorageService.class);

    private final FileStorageProvider provider;
    private final String bucketName;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    protected AbstractS3CompatibleFileStorageService(
            FileStorageProvider provider,
            String bucketName,
            S3Client s3Client,
            S3Presigner s3Presigner
    ) {
        this.provider = provider;
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public FileStorageProvider provider() {
        return provider;
    }

    @Override
    public boolean isAvailable() {
        return s3Client != null && bucketName != null && !bucketName.isBlank();
    }

    @Override
    public StoredFile store(StoreFileRequest request) {
        ensureAvailable();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(request.objectKey())
                            .contentType(request.contentType())
                            .contentLength((long) request.content().length)
                            .build(),
                    RequestBody.fromBytes(request.content())
            );
            return new StoredFile(provider, bucketName, request.objectKey(), request.contentType(), request.content().length);
        } catch (S3Exception ex) {
            throw new FileStorageException("Failed to store file in " + provider.code(), ex);
        }
    }

    @Override
    public StoredFileContent load(FileReference fileReference) {
        ensureAvailable();
        try {
            byte[] content = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(resolveBucket(fileReference))
                            .key(fileReference.objectKey())
                            .build()
            ).asByteArray();
            return new StoredFileContent(fileReference.originalFileName(), fileReference.contentType(), content);
        } catch (NoSuchKeyException ex) {
            throw new FileStorageException("Stored file was not found", ex);
        } catch (S3Exception ex) {
            throw new FileStorageException("Failed to load file from " + provider.code(), ex);
        }
    }

    @Override
    public AccessUrl createDownloadAccess(FileReference fileReference, long ttlMinutes) {
        ensureAvailable();
        if (s3Presigner == null || ttlMinutes < 1) {
            return null;
        }
        try {
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(ttlMinutes))
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(resolveBucket(fileReference))
                                    .key(fileReference.objectKey())
                                    .responseContentType(fileReference.contentType())
                                    .responseContentDisposition("attachment; filename=\"" + sanitizeFileName(fileReference.originalFileName()) + "\"")
                                    .build())
                            .build()
            );
            return new AccessUrl(
                    presignedRequest.url().toString(),
                    Instant.now().plus(Duration.ofMinutes(ttlMinutes)),
                    true
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to create direct download URL for object key={} provider={}: {}", fileReference.objectKey(), provider.code(), ex.getMessage());
            return null;
        }
    }

    @Override
    public void delete(FileReference fileReference) {
        if (!isAvailable()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(resolveBucket(fileReference))
                    .key(fileReference.objectKey())
                    .build());
        } catch (S3Exception ex) {
            log.warn("Failed to delete object key={} from provider={}: {}", fileReference.objectKey(), provider.code(), ex.getMessage());
        }
    }

    protected void ensureAvailable() {
        if (!isAvailable()) {
            throw new FileStorageException("Storage provider " + provider.code() + " is not fully configured");
        }
    }

    private String resolveBucket(FileReference fileReference) {
        if (fileReference.bucketName() != null && !fileReference.bucketName().isBlank()) {
            return fileReference.bucketName();
        }
        return bucketName;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }
}

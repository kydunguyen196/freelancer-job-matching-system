package com.skillbridge.proposal_service.service;

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

abstract class AbstractS3CompatibleFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(AbstractS3CompatibleFileStorageService.class);

    private final FileStorageProvider provider;
    private final String bucketName;
    private final S3Client s3Client;

    protected AbstractS3CompatibleFileStorageService(FileStorageProvider provider, String bucketName, S3Client s3Client) {
        this.provider = provider;
        this.bucketName = bucketName;
        this.s3Client = s3Client;
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
}

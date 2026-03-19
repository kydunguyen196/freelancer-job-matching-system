package com.skillbridge.proposal_service.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skillbridge.proposal_service.config.FileStorageProperties;
import com.skillbridge.proposal_service.domain.FileStorageProvider;

public class MockFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(MockFileStorageService.class);

    private final Path baseDirectory;
    private final String bucketName;

    public MockFileStorageService(FileStorageProperties properties) {
        this.baseDirectory = Paths.get(properties.getMock().getBaseDir()).normalize();
        this.bucketName = properties.getMock().getBucket();
    }

    @Override
    public FileStorageProvider provider() {
        return FileStorageProvider.MOCK;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public StoredFile store(StoreFileRequest request) {
        Path target = resolveObjectPath(request.objectKey());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, request.content());
            return new StoredFile(provider(), bucketName, request.objectKey(), request.contentType(), request.content().length);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file in mock storage", ex);
        }
    }

    @Override
    public StoredFileContent load(FileReference fileReference) {
        Path target = resolveObjectPath(fileReference.objectKey());
        try {
            return new StoredFileContent(fileReference.originalFileName(), fileReference.contentType(), Files.readAllBytes(target));
        } catch (IOException ex) {
            throw new FileStorageException("Failed to load file from mock storage", ex);
        }
    }

    @Override
    public AccessUrl createDownloadAccess(FileReference fileReference, long ttlMinutes) {
        return null;
    }

    @Override
    public void delete(FileReference fileReference) {
        Path target = resolveObjectPath(fileReference.objectKey());
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Failed to delete mock object key={}: {}", fileReference.objectKey(), ex.getMessage());
        }
    }

    private Path resolveObjectPath(String objectKey) {
        Path path = baseDirectory.resolve(objectKey).normalize();
        if (!path.startsWith(baseDirectory)) {
            throw new FileStorageException("Resolved storage path is invalid");
        }
        return path;
    }
}

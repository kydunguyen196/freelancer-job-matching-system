package com.skillbridge.proposal_service.service;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skillbridge.proposal_service.config.FileStorageProperties;
import com.skillbridge.proposal_service.domain.FileStorageProvider;

public class RoutingFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(RoutingFileStorageService.class);

    private final FileStorageProperties properties;
    private final Map<FileStorageProvider, FileStorageService> providers;
    private final FileStorageService mockStorage;

    public RoutingFileStorageService(
            FileStorageProperties properties,
            FileStorageService s3Storage,
            FileStorageService minioStorage,
            FileStorageService mockStorage
    ) {
        this.properties = properties;
        this.mockStorage = mockStorage;
        this.providers = new EnumMap<>(FileStorageProvider.class);
        providers.put(s3Storage.provider(), s3Storage);
        providers.put(minioStorage.provider(), minioStorage);
        providers.put(mockStorage.provider(), mockStorage);
    }

    @Override
    public FileStorageProvider provider() {
        return activeUploadProvider().provider();
    }

    @Override
    public boolean isAvailable() {
        return activeUploadProvider().isAvailable();
    }

    @Override
    public StoredFile store(StoreFileRequest request) {
        return activeUploadProvider().store(request);
    }

    @Override
    public StoredFileContent load(FileReference fileReference) {
        return providerFor(fileReference.provider()).load(fileReference);
    }

    @Override
    public void delete(FileReference fileReference) {
        providerFor(fileReference.provider()).delete(fileReference);
    }

    private FileStorageService activeUploadProvider() {
        FileStorageProvider configuredProvider = FileStorageProvider.from(properties.getProvider());
        FileStorageService service = providers.get(configuredProvider);
        if (service != null && service.isAvailable()) {
            return service;
        }
        if (configuredProvider != FileStorageProvider.MOCK) {
            log.warn("Storage provider {} is unavailable or incomplete; falling back to mock storage", configuredProvider.code());
        }
        return mockStorage;
    }

    private FileStorageService providerFor(FileStorageProvider provider) {
        FileStorageService service = providers.get(provider);
        if (service == null) {
            throw new FileStorageException("No storage implementation registered for provider " + provider.code());
        }
        return service;
    }
}

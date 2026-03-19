package com.skillbridge.proposal_service.domain;

import java.util.Locale;

public enum FileStorageProvider {
    S3("s3"),
    MINIO("minio"),
    MOCK("mock");

    private final String code;

    FileStorageProvider(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static FileStorageProvider from(String value) {
        if (value == null || value.isBlank()) {
            return MOCK;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (FileStorageProvider provider : values()) {
            if (provider.code.equals(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported storage provider: " + value);
    }
}

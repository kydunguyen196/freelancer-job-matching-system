package com.skillbridge.proposal_service.service;

import java.net.URI;

import com.skillbridge.proposal_service.config.FileStorageProperties;
import com.skillbridge.proposal_service.domain.FileStorageProvider;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

public class MinioFileStorageService extends AbstractS3CompatibleFileStorageService {

    public MinioFileStorageService(FileStorageProperties properties) {
        super(
                FileStorageProvider.MINIO,
                properties.getMinio().getBucket(),
                createClient(properties)
        );
    }

    private static S3Client createClient(FileStorageProperties properties) {
        FileStorageProperties.MinioProperties minio = properties.getMinio();
        if (isBlank(minio.getEndpoint()) || isBlank(minio.getBucket()) || isBlank(minio.getAccessKey()) || isBlank(minio.getSecretKey())) {
            return null;
        }
        return S3Client.builder()
                .region(Region.of(minio.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getAccessKey(), minio.getSecretKey())
                ))
                .endpointOverride(URI.create(normalizeEndpoint(minio)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static String normalizeEndpoint(FileStorageProperties.MinioProperties minio) {
        String endpoint = minio.getEndpoint().trim();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return (minio.isSecure() ? "https://" : "http://") + endpoint;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

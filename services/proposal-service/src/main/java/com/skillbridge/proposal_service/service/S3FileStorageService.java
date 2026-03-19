package com.skillbridge.proposal_service.service;

import com.skillbridge.proposal_service.config.FileStorageProperties;
import com.skillbridge.proposal_service.domain.FileStorageProvider;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public class S3FileStorageService extends AbstractS3CompatibleFileStorageService {

    public S3FileStorageService(FileStorageProperties properties) {
        super(
                FileStorageProvider.S3,
                properties.getS3().getBucket(),
                createClient(properties),
                createPresigner(properties)
        );
    }

    private static S3Client createClient(FileStorageProperties properties) {
        FileStorageProperties.S3Properties s3 = properties.getS3();
        if (isBlank(s3.getBucket()) || isBlank(s3.getRegion()) || isBlank(s3.getAccessKeyId()) || isBlank(s3.getSecretAccessKey())) {
            return null;
        }
        return S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKeyId(), s3.getSecretAccessKey())
                ))
                .build();
    }

    private static S3Presigner createPresigner(FileStorageProperties properties) {
        FileStorageProperties.S3Properties s3 = properties.getS3();
        if (isBlank(s3.getBucket()) || isBlank(s3.getRegion()) || isBlank(s3.getAccessKeyId()) || isBlank(s3.getSecretAccessKey())) {
            return null;
        }
        return S3Presigner.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKeyId(), s3.getSecretAccessKey())
                ))
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.skillbridge.proposal_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.skillbridge.proposal_service.service.FileStorageService;
import com.skillbridge.proposal_service.service.MinioFileStorageService;
import com.skillbridge.proposal_service.service.MockFileStorageService;
import com.skillbridge.proposal_service.service.RoutingFileStorageService;
import com.skillbridge.proposal_service.service.S3FileStorageService;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfig.class);

    @Bean
    FileStorageService fileStorageService(FileStorageProperties properties) {
        FileStorageService s3 = new S3FileStorageService(properties);
        FileStorageService minio = new MinioFileStorageService(properties);
        FileStorageService mock = new MockFileStorageService(properties);
        log.info("Initialized file storage with configured provider={}", properties.getProvider());
        return new RoutingFileStorageService(properties, s3, minio, mock);
    }
}

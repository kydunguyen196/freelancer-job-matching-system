package com.skillbridge.proposal_service.config;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    private boolean enabled;
    private String provider = "mock";
    private long maxCvFileSizeMb = 10;
    private List<String> allowedCvContentTypes = new ArrayList<>();
    private final S3Properties s3 = new S3Properties();
    private final MinioProperties minio = new MinioProperties();
    private final MockProperties mock = new MockProperties();

    public FileStorageProperties() {
        allowedCvContentTypes.add("application/pdf");
        allowedCvContentTypes.add("application/msword");
        allowedCvContentTypes.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mock.setBaseDir(Paths.get(System.getProperty("java.io.tmpdir"), "skillbridge", "mock-storage").toString());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getMaxCvFileSizeMb() {
        return maxCvFileSizeMb;
    }

    public void setMaxCvFileSizeMb(long maxCvFileSizeMb) {
        this.maxCvFileSizeMb = maxCvFileSizeMb;
    }

    public List<String> getAllowedCvContentTypes() {
        return allowedCvContentTypes;
    }

    public void setAllowedCvContentTypes(List<String> allowedCvContentTypes) {
        this.allowedCvContentTypes = allowedCvContentTypes;
    }

    public S3Properties getS3() {
        return s3;
    }

    public MinioProperties getMinio() {
        return minio;
    }

    public MockProperties getMock() {
        return mock;
    }

    public long maxCvFileSizeBytes() {
        return maxCvFileSizeMb * 1024L * 1024L;
    }

    public static class S3Properties {
        private String bucket;
        private String region;
        private String accessKeyId;
        private String secretAccessKey;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getSecretAccessKey() {
            return secretAccessKey;
        }

        public void setSecretAccessKey(String secretAccessKey) {
            this.secretAccessKey = secretAccessKey;
        }
    }

    public static class MinioProperties {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String region = "us-east-1";
        private boolean secure;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }
    }

    public static class MockProperties {
        private String baseDir;
        private String bucket = "mock-local";

        public String getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}

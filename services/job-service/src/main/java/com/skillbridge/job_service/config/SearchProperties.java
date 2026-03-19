package com.skillbridge.job_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    private boolean enabled;
    private String provider = "db";
    private final OpenSearchProperties opensearch = new OpenSearchProperties();

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

    public OpenSearchProperties getOpensearch() {
        return opensearch;
    }

    public static class OpenSearchProperties {
        private String url;
        private String username;
        private String password;
        private String indexJobs = "jobs";
        private String indexCompanies = "companies";
        private int connectTimeoutMs = 3000;
        private int socketTimeoutMs = 5000;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getIndexJobs() {
            return indexJobs;
        }

        public void setIndexJobs(String indexJobs) {
            this.indexJobs = indexJobs;
        }

        public String getIndexCompanies() {
            return indexCompanies;
        }

        public void setIndexCompanies(String indexCompanies) {
            this.indexCompanies = indexCompanies;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getSocketTimeoutMs() {
            return socketTimeoutMs;
        }

        public void setSocketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
        }
    }
}

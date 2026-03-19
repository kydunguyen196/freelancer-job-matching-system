package com.skillbridge.notification_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private boolean enabled;
    private String sendgridApiKey;
    private String fromEmail;
    private String fromName = "SkillBridge";
    private String sendgridBaseUrl = "https://api.sendgrid.com";
    private int maxAttempts = 5;
    private long initialRetryDelaySeconds = 60;
    private double retryMultiplier = 2.0d;
    private long retryPollDelayMs = 60000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSendgridApiKey() {
        return sendgridApiKey;
    }

    public void setSendgridApiKey(String sendgridApiKey) {
        this.sendgridApiKey = sendgridApiKey;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getSendgridBaseUrl() {
        return sendgridBaseUrl;
    }

    public void setSendgridBaseUrl(String sendgridBaseUrl) {
        this.sendgridBaseUrl = sendgridBaseUrl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialRetryDelaySeconds() {
        return initialRetryDelaySeconds;
    }

    public void setInitialRetryDelaySeconds(long initialRetryDelaySeconds) {
        this.initialRetryDelaySeconds = initialRetryDelaySeconds;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public long getRetryPollDelayMs() {
        return retryPollDelayMs;
    }

    public void setRetryPollDelayMs(long retryPollDelayMs) {
        this.retryPollDelayMs = retryPollDelayMs;
    }
}

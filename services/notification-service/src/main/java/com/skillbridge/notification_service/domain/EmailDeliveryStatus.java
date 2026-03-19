package com.skillbridge.notification_service.domain;

public enum EmailDeliveryStatus {
    PENDING,
    PROCESSING,
    RETRY_PENDING,
    SENT,
    FAILED
}

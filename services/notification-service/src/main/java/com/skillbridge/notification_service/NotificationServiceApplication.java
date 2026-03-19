package com.skillbridge.notification_service;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        normalizeTimezoneAlias();
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    private static void normalizeTimezoneAlias() {
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
    }
}

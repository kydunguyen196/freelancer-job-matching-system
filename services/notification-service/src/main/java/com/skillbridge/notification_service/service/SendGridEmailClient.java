package com.skillbridge.notification_service.service;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.skillbridge.notification_service.config.EmailProperties;

@Component
public class SendGridEmailClient {

    private final RestClient restClient;
    private final EmailProperties emailProperties;

    public SendGridEmailClient(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
        this.restClient = RestClient.builder()
                .baseUrl(emailProperties.getSendgridBaseUrl())
                .build();
    }

    public void sendEmail(String recipientEmail, String subject, String body) {
        if (emailProperties.getSendgridApiKey() == null || emailProperties.getSendgridApiKey().isBlank()) {
            throw new IllegalStateException("SENDGRID_API_KEY is missing");
        }
        if (emailProperties.getFromEmail() == null || emailProperties.getFromEmail().isBlank()) {
            throw new IllegalStateException("MAIL_FROM_EMAIL is missing");
        }

        restClient.post()
                .uri("/v3/mail/send")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + emailProperties.getSendgridApiKey())
                .body(new SendGridMailRequest(
                        new EmailAddress(emailProperties.getFromEmail(), emailProperties.getFromName()),
                        List.of(new Personalization(List.of(new EmailAddress(recipientEmail, null)))),
                        subject,
                        List.of(new Content("text/plain", body))
                ))
                .retrieve()
                .toBodilessEntity();
    }

    public record SendGridMailRequest(
            EmailAddress from,
            List<Personalization> personalizations,
            String subject,
            List<Content> content
    ) {
    }

    public record EmailAddress(
            String email,
            String name
    ) {
    }

    public record Personalization(
            List<EmailAddress> to
    ) {
    }

    public record Content(
            String type,
            String value
    ) {
    }
}

package com.skillbridge.proposal_service.service;

import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.skillbridge.proposal_service.config.CalendarProperties;

@Service
public class GoogleCalendarService implements CalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private final CalendarProperties calendarProperties;
    private final RestClient restClient;

    public GoogleCalendarService(CalendarProperties calendarProperties) {
        this.calendarProperties = calendarProperties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public CreateInterviewEventResult createInterviewEvent(CreateInterviewEventRequest request) {
        if (!calendarProperties.isEnabled()) {
            return CreateInterviewEventResult.skipped();
        }
        if (missingRequiredConfiguration()) {
            log.warn("Google Calendar integration is enabled but configuration is incomplete");
            return CreateInterviewEventResult.warning("Google Calendar integration is enabled but not fully configured");
        }

        try {
            String accessToken = fetchAccessToken();
            GoogleCalendarEventResponse response = restClient.post()
                    .uri(calendarProperties.getApiBaseUrl() + "/calendar/v3/calendars/{calendarId}/events?sendUpdates=all",
                            calendarProperties.getCalendarId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .body(toEventRequest(request))
                    .retrieve()
                    .body(GoogleCalendarEventResponse.class);
            return response == null || response.id() == null
                    ? CreateInterviewEventResult.warning("Google Calendar did not return an event id")
                    : CreateInterviewEventResult.success(response.id());
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Failed to create Google Calendar event for proposalId={}", request.proposalId(), ex);
            return CreateInterviewEventResult.warning("Google Calendar event could not be created");
        }
    }

    private boolean missingRequiredConfiguration() {
        return isBlank(calendarProperties.getClientId())
                || isBlank(calendarProperties.getClientSecret())
                || isBlank(calendarProperties.getCalendarId())
                || isBlank(calendarProperties.getRefreshToken());
    }

    private String fetchAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", calendarProperties.getClientId());
        form.add("client_secret", calendarProperties.getClientSecret());
        form.add("refresh_token", calendarProperties.getRefreshToken());
        form.add("grant_type", "refresh_token");

        GoogleTokenResponse response = restClient.post()
                .uri(calendarProperties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(GoogleTokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Google token response did not contain an access token");
        }
        return response.accessToken();
    }

    private GoogleCalendarEventRequest toEventRequest(CreateInterviewEventRequest request) {
        return new GoogleCalendarEventRequest(
                "Interview - " + request.jobTitle(),
                buildDescription(request),
                new EventDateTime(request.startTime().toString(), ZoneId.systemDefault().getId()),
                new EventDateTime(request.endTime().toString(), ZoneId.systemDefault().getId()),
                List.of(
                        new Attendee(request.candidateEmail()),
                        new Attendee(request.recruiterEmail())
                )
        );
    }

    private String buildDescription(CreateInterviewEventRequest request) {
        String notes = request.notes() == null ? "" : request.notes().trim();
        return """
                Job:
                - Job ID: %s
                - Title: %s

                Candidate:
                - User ID: %s
                - Email: %s

                Recruiter:
                - User ID: %s
                - Email: %s

                Proposal:
                - Proposal ID: %s
                - Price: %s
                - Duration Days: %s
                - Cover Letter: %s

                Schedule:
                - Start: %s
                - End: %s
                - Meeting Link: %s

                Notes:
                %s
                """.formatted(
                request.jobId(),
                request.jobTitle(),
                request.candidateUserId(),
                request.candidateEmail(),
                request.recruiterUserId(),
                request.recruiterEmail(),
                request.proposalId(),
                request.proposalPrice(),
                request.proposalDurationDays(),
                request.coverLetter() == null || request.coverLetter().isBlank() ? "N/A" : request.coverLetter(),
                request.startTime(),
                request.endTime(),
                request.meetingLink() == null || request.meetingLink().isBlank() ? "N/A" : request.meetingLink(),
                notes.isBlank() ? "N/A" : notes
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record GoogleTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn,
            String scope,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType
    ) {
    }

    private record GoogleCalendarEventRequest(
            String summary,
            String description,
            EventDateTime start,
            EventDateTime end,
            List<Attendee> attendees
    ) {
    }

    private record EventDateTime(
            @com.fasterxml.jackson.annotation.JsonProperty("dateTime") String dateTime,
            String timeZone
    ) {
    }

    private record Attendee(
            String email
    ) {
    }

    private record GoogleCalendarEventResponse(
            String id
    ) {
    }
}

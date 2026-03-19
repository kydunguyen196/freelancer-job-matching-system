package com.skillbridge.proposal_service.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleInterviewRequest(
        @NotNull @Future Instant interviewScheduledAt,
        @NotNull @Future Instant interviewEndsAt,
        @Size(max = 512) String meetingLink,
        @Size(max = 2000) String notes
) {
}

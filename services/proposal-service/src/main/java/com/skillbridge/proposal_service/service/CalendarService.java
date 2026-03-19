package com.skillbridge.proposal_service.service;

import java.time.Instant;
import java.math.BigDecimal;

public interface CalendarService {

    CreateInterviewEventResult createInterviewEvent(CreateInterviewEventRequest request);

    record CreateInterviewEventRequest(
            Long proposalId,
            Long jobId,
            String jobTitle,
            Long candidateUserId,
            String candidateEmail,
            Long recruiterUserId,
            String recruiterEmail,
            BigDecimal proposalPrice,
            Integer proposalDurationDays,
            String coverLetter,
            Instant startTime,
            Instant endTime,
            String meetingLink,
            String notes
    ) {
    }

    record CreateInterviewEventResult(
            String externalEventId,
            String warning
    ) {
        public static CreateInterviewEventResult success(String externalEventId) {
            return new CreateInterviewEventResult(externalEventId, null);
        }

        public static CreateInterviewEventResult skipped() {
            return new CreateInterviewEventResult(null, null);
        }

        public static CreateInterviewEventResult warning(String warning) {
            return new CreateInterviewEventResult(null, warning);
        }
    }
}

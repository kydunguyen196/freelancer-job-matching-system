package com.skillbridge.proposal_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectProposalRequest(
        @NotBlank @Size(max = 2000) String feedbackMessage
) {
}

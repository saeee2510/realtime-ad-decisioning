package com.saee.addecision.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClickRequest(
        @NotBlank String requestId,
        @NotNull Long campaignId) {
}

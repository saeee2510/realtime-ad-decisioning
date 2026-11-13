package com.saee.addecision.api;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record DecisionRequest(
        @NotBlank String userSegment,
        @NotBlank String placement,
        Map<String, String> context) {
}

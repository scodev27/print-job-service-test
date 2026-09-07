package com.adobe.printservice.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request body for POST /jobs.
 */
public record CreateJobRequest(
        @NotBlank String templateId,
        Map<String, Object> parameters
) {
}
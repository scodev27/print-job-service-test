package com.adobe.printservice.web.dto;

import com.adobe.printservice.model.JobStatus;

import java.time.Instant;

/**
 * Response body for job endpoints. Deliberately excludes resultContent and errorMessage
 * details that belong to GET /jobs/{id}/result, keeping the status payload lightweight.
 */
public record JobResponse(
        String id,
        String templateId,
        JobStatus status,
        int attempts,
        String errorMessage,
        boolean resultAvailable,
        Instant createdAt,
        Instant updatedAt
) {
}
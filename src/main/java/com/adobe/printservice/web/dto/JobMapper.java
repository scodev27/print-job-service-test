package com.adobe.printservice.web.dto;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;

public final class JobMapper {

    private JobMapper() {
    }

    public static JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTemplateId(),
                job.getStatus(),
                job.getAttempts(),
                job.getErrorMessage(),
                job.getStatus() == JobStatus.DONE && job.getResultContent() != null,
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
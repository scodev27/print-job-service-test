package com.adobe.printservice.web;

import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Exposes job counts grouped by status at /actuator/jobmetrics. A plain custom
 * endpoint rather than Micrometer gauges, since a point-in-time count queried
 * directly from the database is simpler and sufficient for current requirements.
 */
@Component
@Endpoint(id = "jobmetrics")
public class JobMetricsEndpoint {

    private final JobRepository jobRepository;

    public JobMetricsEndpoint(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @ReadOperation
    public Map<JobStatus, Long> jobCountsByStatus() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            counts.put(status, jobRepository.countByStatus(status));
        }
        return counts;
    }
}
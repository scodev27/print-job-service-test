package com.adobe.printservice.web;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.service.JobService;
import com.adobe.printservice.web.dto.CreateJobRequest;
import com.adobe.printservice.web.dto.ErrorResponse;
import com.adobe.printservice.web.dto.JobMapper;
import com.adobe.printservice.web.dto.JobResponse;
import com.adobe.printservice.web.dto.JobResultResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/jobs")
public class JobResource {

    private final JobService jobService;

    public JobResource(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> submit(@Valid @RequestBody CreateJobRequest request) {
        Job job = jobService.submit(request.templateId(), request.parameters());
        return ResponseEntity.status(HttpStatus.CREATED).body(JobMapper.toResponse(job));
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable String id) {
        return JobMapper.toResponse(jobService.getById(id));
    }

    @GetMapping
    public List<JobResponse> listJobs(@RequestParam(required = false) JobStatus status) {
        return jobService.list(status).stream()
                .map(JobMapper::toResponse)
                .toList();
    }

    /**
     * Design decision: DONE returns the result with 200. FAILED returns 409 (the
     * resource exists but is in a terminal, unrecoverable state for this request).
     * QUEUED/PROCESSING returns 202 to signal "not an error, try again later" -
     * distinct from a 404, since the job does exist.
     */
    @GetMapping("/{id}/result")
    public ResponseEntity<?> getResult(@PathVariable String id) {
        Job job = jobService.getById(id);
        return switch (job.getStatus()) {
            case DONE -> ResponseEntity.ok(new JobResultResponse(job.getResultContent()));
            case FAILED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Job failed: " + job.getErrorMessage()));
            case QUEUED, PROCESSING -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new ErrorResponse("Job is still " + job.getStatus().name().toLowerCase()));
        };
    }
}
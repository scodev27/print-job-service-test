package com.adobe.printservice.worker;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Processes a single job within its own transaction. Split out from JobWorker so that
 * @Transactional is applied through Spring's proxy - calling this method from another
 * bean (rather than from within the same class) is what makes the transaction interceptor
 * actually engage.
 */
@Component
public class JobProcessor {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final double SIMULATED_FAILURE_RATE = 0.3;

    private final JobRepository jobRepository;
    private final Random random = new Random();

    public JobProcessor(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Attempts to claim the job before processing it. If another instance already
     * claimed it between the poll query and this call, the claim returns 0 rows
     * updated and this method exits without side effects.
     */
    @Transactional
    public void processIfClaimed(String jobId) {
        if (jobRepository.claim(jobId, Instant.now()) == 0) {
            return;
        }
        Job job = jobRepository.findById(jobId).orElseThrow();
        simulateRendering(job);
        jobRepository.save(job);
    }

    private void simulateRendering(Job job) {
        sleep(Duration.ofMillis(500));
        if (random.nextDouble() < SIMULATED_FAILURE_RATE) {
            onFailure(job, "Simulated transient rendering failure");
            return;
        }
        job.setResultContent("Rendered output for template " + job.getTemplateId());
        job.setErrorMessage(null);
        job.setStatus(JobStatus.DONE);
        job.setUpdatedAt(Instant.now());
    }

    /**
     * Requeues the job with an exponential backoff delay if attempts remain,
     * otherwise marks it FAILED permanently.
     */
    private void onFailure(Job job, String reason) {
        int attempts = job.getAttempts() + 1;
        job.setAttempts(attempts);
        job.setErrorMessage(reason);
        job.setUpdatedAt(Instant.now());
        if (attempts >= MAX_ATTEMPTS) {
            job.setStatus(JobStatus.FAILED);
            return;
        }
        job.setStatus(JobStatus.QUEUED);
        job.setNextAttemptAt(Instant.now().plus(BASE_BACKOFF.multipliedBy(1L << attempts)));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
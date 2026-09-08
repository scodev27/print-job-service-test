package com.adobe.printservice.worker;

import com.adobe.printservice.repository.JobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Polls for queued jobs ready to be processed and delegates each one to JobProcessor.
 * Safe to run as multiple instances against the same database: JobProcessor's atomic
 * claim ensures only one instance ever wins the race to process a given job.
 */
@Component
public class JobWorker {

    private final JobRepository jobRepository;
    private final JobProcessor jobProcessor;


    public JobWorker(JobRepository jobRepository, JobProcessor jobProcessor) {
        this.jobRepository = jobRepository;
        this.jobProcessor = jobProcessor;
    }

    @Scheduled(fixedDelayString = "${job.worker.poll-interval-ms:2000}")
    public void pollQueuedJobs() {
        jobRepository.findQueuedReadyForProcessing(Instant.now())
                .forEach(job -> jobProcessor.processIfClaimed(job.getId()));
    }
}
package com.adobe.printservice.worker;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProcessorTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RenderExecutor renderExecutor;

    @InjectMocks
    private JobProcessor jobProcessor;

    @Test
    void processIfClaimed_jobAlreadyClaimedByAnotherInstance_doesNothing() {
        when(jobRepository.claim(eq("job-1"), any(Instant.class))).thenReturn(0);

        jobProcessor.processIfClaimed("job-1");

        verify(jobRepository, never()).findById(anyString());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void processIfClaimed_renderingSucceeds_marksJobDone() {
        Job job = newJob();
        when(jobRepository.claim(eq(job.getId()), any(Instant.class))).thenReturn(1);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(renderExecutor.render(job)).thenReturn("rendered output");

        jobProcessor.processIfClaimed(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getResultContent()).isEqualTo("rendered output");
        assertThat(job.getErrorMessage()).isNull();
        verify(jobRepository).save(job);
    }

    @Test
    void processIfClaimed_renderingFailsBelowMaxAttempts_requeuesWithBackoff() {
        Job job = newJob();
        job.setAttempts(0);
        when(jobRepository.claim(eq(job.getId()), any(Instant.class))).thenReturn(1);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(renderExecutor.render(job)).thenThrow(new RenderException("boom"));

        jobProcessor.processIfClaimed(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getErrorMessage()).isEqualTo("boom");
        assertThat(job.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void processIfClaimed_renderingFailsOnLastAttempt_marksJobFailed() {
        Job job = newJob();
        job.setAttempts(2);
        when(jobRepository.claim(eq(job.getId()), any(Instant.class))).thenReturn(1);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(renderExecutor.render(job)).thenThrow(new RenderException("boom"));

        jobProcessor.processIfClaimed(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(3);
    }

    private Job newJob() {
        Job job = new Job();
        job.setTemplateId("template-1");
        return job;
    }
}
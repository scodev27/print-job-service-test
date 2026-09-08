package com.adobe.printservice.service;

import com.adobe.printservice.exception.JobNotFoundException;
import com.adobe.printservice.exception.TemplateNotFoundException;
import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import com.adobe.printservice.repository.RenderTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RenderTemplateRepository renderTemplateRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void submit_existingTemplate_savesAndReturnsJob() {
        when(renderTemplateRepository.existsById("template-1")).thenReturn(true);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job job = jobService.submit("template-1", Map.of("key", "value"));

        assertThat(job.getTemplateId()).isEqualTo("template-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void submit_unknownTemplate_throwsTemplateNotFoundException() {
        when(renderTemplateRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> jobService.submit("missing", Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);

        verify(jobRepository, never()).save(any());
    }

    @Test
    void getById_existingJob_returnsJob() {
        Job job = new Job();
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThat(jobService.getById("job-1")).isSameAs(job);
    }

    @Test
    void getById_missingJob_throwsJobNotFoundException() {
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getById("missing"))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void list_withStatusFilter_delegatesToFindByStatus() {
        when(jobRepository.findByStatus(JobStatus.FAILED)).thenReturn(List.of(new Job()));

        List<Job> jobs = jobService.list(JobStatus.FAILED);

        assertThat(jobs).hasSize(1);
        verify(jobRepository).findByStatus(JobStatus.FAILED);
        verify(jobRepository, never()).findAll();
    }

    @Test
    void list_withoutStatusFilter_delegatesToFindAll() {
        when(jobRepository.findAll()).thenReturn(List.of(new Job(), new Job()));

        List<Job> jobs = jobService.list(null);

        assertThat(jobs).hasSize(2);
        verify(jobRepository).findAll();
        verify(jobRepository, never()).findByStatus(any());
    }
}
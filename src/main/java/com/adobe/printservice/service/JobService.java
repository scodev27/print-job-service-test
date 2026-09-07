package com.adobe.printservice.service;

import com.adobe.printservice.exception.JobNotFoundException;
import com.adobe.printservice.exception.TemplateNotFoundException;
import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import com.adobe.printservice.repository.RenderTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates job submission and lookup. Deliberately does NOT perform any rendering
 * itself - submit() only validates and persists, returning immediately as required by
 * the API contract. Actual processing is driven by JobWorker.
 */
@Service
public class JobService {

    private final JobRepository jobRepository;
    private final RenderTemplateRepository renderTemplateRepository;

    public JobService(JobRepository jobRepository, RenderTemplateRepository renderTemplateRepository) {
        this.jobRepository = jobRepository;
        this.renderTemplateRepository = renderTemplateRepository;
    }

    @Transactional
    public Job submit(String templateId, Map<String, Object> parameters) {
        if (!renderTemplateRepository.existsById(templateId)) {
            throw new TemplateNotFoundException(templateId);
        }
        Job job = new Job();
        job.setTemplateId(templateId);
        job.setParameters(parameters);
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Job getById(String id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Job> list(JobStatus status) {
        return status != null ? jobRepository.findByStatus(status) : jobRepository.findAll();
    }
}
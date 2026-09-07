package com.adobe.printservice.repository;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {

    List<Job> findByStatus(JobStatus status);

    /**
     * Atomically transitions a single job from QUEUED to PROCESSING.
     * Returns the number of updated rows: 1 if this instance won the race to claim the job,
     * 0 if another instance already claimed it first. This is what makes horizontal scaling
     * of the worker safe without any external locking service.
     */
    @Modifying
    @Query("""
        update Job j
           set j.status = com.adobe.printservice.model.JobStatus.PROCESSING,
               j.updatedAt = :now
         where j.id = :id
           and j.status = com.adobe.printservice.model.JobStatus.QUEUED
        """)
    int claim(@Param("id") String id, @Param("now") Instant now);

    /**
     * Candidates for the next poll cycle: queued jobs with no backoff pending,
     * or whose backoff window has already elapsed.
     */
    @Query("""
        select j from Job j
         where j.status = com.adobe.printservice.model.JobStatus.QUEUED
           and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
        """)
    List<Job> findQueuedReadyForProcessing(@Param("now") Instant now);
}
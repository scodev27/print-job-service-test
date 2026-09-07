package com.adobe.printservice.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
    }
}
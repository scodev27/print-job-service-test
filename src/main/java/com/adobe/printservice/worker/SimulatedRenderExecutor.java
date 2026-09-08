package com.adobe.printservice.worker;

import com.adobe.printservice.model.Job;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Random;

/**
 * Placeholder rendering strategy: simulates work with a short delay and a configurable
 * chance of transient failure, standing in for a real rendering engine.
 */
@Component
public class SimulatedRenderExecutor implements RenderExecutor {

    private static final double FAILURE_RATE = 0.3;

    private final Random random = new Random();

    @Override
    public String render(Job job) {
        sleep(Duration.ofMillis(500));
        if (random.nextDouble() < FAILURE_RATE) {
            throw new RenderException("Simulated transient rendering failure");
        }
        return "Rendered output for template " + job.getTemplateId();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
package com.acme.clm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scans for overdue workflow tasks and escalates them (plan §7.3). */
@Component
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);
    private final WorkflowService workflow;

    public SlaScheduler(WorkflowService workflow) { this.workflow = workflow; }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void sweep() {
        try {
            int n = workflow.escalateOverdue();
            if (n > 0) log.info("SLA sweep escalated {} overdue task(s)", n);
        } catch (Exception e) {
            log.warn("SLA sweep failed: {}", e.getMessage());
        }
    }
}

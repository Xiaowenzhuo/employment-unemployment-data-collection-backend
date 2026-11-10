package com.example.employment.backend.national.service;

import com.example.employment.backend.national.repository.NationalRepository;
import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableAsync
@ConditionalOnProperty(name="app.national.role",havingValue="sender",matchIfMissing=true)
public class NationalRecovery {
    private final NationalRepository batches;
    private final NationalSender sender;
    public NationalRecovery(NationalRepository batches, NationalSender sender) {
        this.batches = batches;
        this.sender = sender;
    }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recover() {
        batches.recover(Instant.now());
        // Send only after the recovery transaction commits.
        var queued = batches.queued();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { queued.forEach(sender::send); }
                });
    }
}

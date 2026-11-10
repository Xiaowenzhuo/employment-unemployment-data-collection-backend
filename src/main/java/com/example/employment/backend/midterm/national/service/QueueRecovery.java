package com.example.employment.backend.midterm.national.service;

import com.example.employment.backend.midterm.national.repository.QueueRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(name="app.national.role",havingValue="sender",matchIfMissing=true)
public class QueueRecovery {
    private final QueueRepository queue;
    private final QueueSender sender;
    public QueueRecovery(QueueRepository queue,QueueSender sender) {
        this.queue=queue;this.sender=sender;
    }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recover() {
        queue.recover(Instant.now());
        var pending=queue.queued().stream().map(b->b.id()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { pending.forEach(sender::send); }
        });
    }
}

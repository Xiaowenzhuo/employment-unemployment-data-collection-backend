package com.example.employment.backend.midterm.warehouse.service;

import com.example.employment.backend.midterm.warehouse.repository.WarehouseRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;

@Service
@EnableScheduling
public class WarehouseSyncService {
    private static final Logger log=LoggerFactory.getLogger(WarehouseSyncService.class);
    private final WarehouseRepository warehouse;
    private final TransactionTemplate tx;
    private final boolean receiver;
    public WarehouseSyncService(WarehouseRepository warehouse,TransactionTemplate tx,
            @Value("${app.national.role:sender}") String role) {
        this.warehouse=warehouse;this.tx=tx;this.receiver="receiver".equals(role);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void onStart() { sync(); }
    @Scheduled(fixedDelay=60000)
    public synchronized void sync() {
        if(receiver) return;
        LocalDateTime started=LocalDateTime.now(ZoneOffset.UTC);
        try {
            tx.executeWithoutResult(status->{
                int rows=warehouse.replaceSnapshot(LocalDateTime.now(ZoneOffset.UTC));
                warehouse.run(started,LocalDateTime.now(ZoneOffset.UTC),"SUCCEEDED",rows,null);
            });
        } catch (RuntimeException ex) {
            log.error("Warehouse sync failed; previous snapshot remains available",ex);
            String message=ex.getMessage()==null?"同步失败":ex.getMessage();
            if (message.length()>500) message=message.substring(0,500);
            String error=message;
            tx.executeWithoutResult(status->warehouse.run(started,
                    LocalDateTime.now(ZoneOffset.UTC),"FAILED",0,error));
        }
    }
}

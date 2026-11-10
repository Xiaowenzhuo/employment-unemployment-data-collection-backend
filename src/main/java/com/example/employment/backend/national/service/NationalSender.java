package com.example.employment.backend.national.service;

import com.example.employment.backend.national.dao.NationalDao.BatchRow;
import com.example.employment.backend.national.repository.NationalRepository;
import com.example.employment.backend.national.service.NationalViews.Receipt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class NationalSender {
    private static final Logger log = LoggerFactory.getLogger(NationalSender.class);
    private final NationalRepository batches;
    private final MockNationalAdapter adapter;
    private final TransactionTemplate tx;

    public NationalSender(NationalRepository batches, MockNationalAdapter adapter,
            TransactionTemplate tx) {
        this.batches = batches;
        this.adapter = adapter;
        this.tx = tx;
    }

    @Async
    public void send(String id) {
        Integer number = tx.execute(status -> {
            int next = batches.attemptCount(id) + 1;
            return batches.start(id, next, now()) ? next : null;
        });
        if (number == null) return;
        try {
            BatchRow batch = batches.byId(id).orElseThrow();
            byte[] bytes = batch.payloadJson().getBytes(StandardCharsets.UTF_8);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(batch.payloadSha256())) throw new IllegalStateException("冻结报文哈希不匹配");
            Receipt receipt = adapter.accept(id, bytes, hash, batch.enterpriseCount(), batch.createdAt());
            if (!id.equals(receipt.batchId()) || receipt.acceptedEnterpriseCount() != batch.enterpriseCount()
                    || !hash.equals(receipt.payloadSha256()) || !"ACCEPTED".equals(receipt.result()))
                throw new IllegalStateException("模拟回执与冻结批次不一致");
            tx.executeWithoutResult(status -> batches.accepted(id, number,
                    receipt.mockReceiptId(), receipt.receivedAt(), now()));
        } catch (Exception ex) {
            log.warn("Mock national send failed for batch {}", id, ex);
            String error = ex.getMessage() == null ? "模拟发送失败" : ex.getMessage();
            if (error.length() > 500) error = error.substring(0, 500);
            String summary = error;
            tx.executeWithoutResult(status -> batches.failed(id, number, summary, now()));
        }
    }

    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MILLIS); }
}

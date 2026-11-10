package com.example.employment.backend.national.service;

import com.example.employment.backend.national.service.NationalViews.Receipt;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class MockNationalAdapter {
    private final ConcurrentHashMap<String, Receipt> received = new ConcurrentHashMap<>();

    public Receipt accept(String batchId, byte[] payload, String sha256,
            int enterpriseCount, Instant generatedAt) {
        if (payload.length == 0) throw new IllegalArgumentException("模拟报文为空");
        return received.computeIfAbsent(batchId, id -> new Receipt(id,
                "MOCK-" + UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)),
                "ACCEPTED", generatedAt.plusMillis(1), enterpriseCount, sha256));
    }
}

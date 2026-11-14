package com.example.employment.backend.week78.monitor.service;

import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week78.monitor.repository.MonitorRepository;
import com.sun.management.OperatingSystemMXBean;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {
    private final MonitorRepository repository;
    private final ScopeService scopes;
    private final String version;
    private final String startedAt = Instant.now().toString();
    private final ExecutorService probes = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "monitor-probe");
        thread.setDaemon(true);
        return thread;
    });

    public MonitorService(MonitorRepository repository, ScopeService scopes,
            @Value("${app.build-version}") String version) {
        this.repository = repository;
        this.scopes = scopes;
        this.version = version;
    }

    public Monitor current(UserAccount actor) {
        scopes.province(actor);
        String at = Instant.now().toString();
        Cpu cpu = cpu(at);
        Size memory = memory(at);
        Size disk = disk(at);
        return new Monitor(at,cpu,memory,disk,new Backend("UP",version,startedAt,at),
                probe(repository::mysql),probe(repository::rabbitmq),
                probe(repository::receiver));
    }

    private Cpu cpu(String at) {
        try {
            var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double value = os.getCpuLoad();
            return value < 0 ? new Cpu("UNKNOWN",null,at)
                    : new Cpu("UP",Math.min(100d,Math.max(0d,value*100d)),at);
        } catch (RuntimeException exception) {
            return new Cpu("UNKNOWN",null,at);
        }
    }

    private Size memory(String at) {
        try {
            var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalMemorySize();
            long used = total - os.getFreeMemorySize();
            return total <= 0 ? new Size("UNKNOWN",null,null,at)
                    : new Size("UP",used,total,at);
        } catch (RuntimeException exception) {
            return new Size("UNKNOWN",null,null,at);
        }
    }

    private Size disk(String at) {
        try {
            Path deploymentDirectory = Path.of(System.getProperty("user.dir"));
            var store = Files.getFileStore(deploymentDirectory);
            long total = store.getTotalSpace();
            return new Size("UP",total-store.getUsableSpace(),total,at);
        } catch (Exception exception) {
            return new Size("UNKNOWN",null,null,at);
        }
    }

    private Dependency probe(Callable<Boolean> task) {
        String at = Instant.now().toString();
        var future = probes.submit(task);
        try {
            return new Dependency(future.get(2,TimeUnit.SECONDS) ? "UP" : "DOWN",at);
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            return new Dependency("UNKNOWN",at);
        } catch (Exception exception) {
            return new Dependency("DOWN",at);
        }
    }

    @PreDestroy
    public void shutdown() {
        probes.shutdownNow();
    }

    public record Cpu(String status, Double usagePercent, String checkedAt) {
    }
    public record Size(String status, Long usedBytes, Long totalBytes, String checkedAt) {
    }
    public record Backend(String status, String version, String startedAt, String checkedAt) {
    }
    public record Dependency(String status, String checkedAt) {
    }
    public record Monitor(String sampledAt, Cpu cpu, Size memory, Size disk, Backend backend,
            Dependency mysql, Dependency rabbitmq, Dependency nationalReceiver) {
    }
}

package kr.co.pillguide.backend.api.drug.reference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A separate worker keeps slow MFDS calls off the medication notification scheduler. */
@Component
@ConditionalOnProperty(name = "reference.sync.enabled", havingValue = "true")
public class ReferenceSyncJob {
    private static final Logger log = LoggerFactory.getLogger(ReferenceSyncJob.class);
    private final ReferenceSyncCoordinator coordinator;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "reference-sync");
        thread.setDaemon(true);
        return thread;
    });

    public ReferenceSyncJob(ReferenceSyncCoordinator coordinator) { this.coordinator = coordinator; }

    @PostConstruct
    void start() { worker.scheduleWithFixedDelay(this::runSafely, 60, 86400, TimeUnit.SECONDS); }

    private void runSafely() {
        try { coordinator.synchronize(true); }
        catch (ReferenceSyncException ignored) { /* Stable reason was already recorded. */ }
        catch (RuntimeException ignored) { log.warn("Reference synchronization could not acquire database state"); }
    }

    @PreDestroy
    void stop() { worker.shutdownNow(); }
}

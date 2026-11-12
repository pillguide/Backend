package kr.co.pillguide.backend.api.drug.reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Network collection is deliberately outside the short publication transaction. */
@Service
public class ReferenceSyncCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ReferenceSyncCoordinator.class);
    private final ReferenceSyncService state;
    private final ReferenceCollector collector;
    private final ReferencePublisher publisher;

    public ReferenceSyncCoordinator(ReferenceSyncService state, ReferenceCollector collector, ReferencePublisher publisher) {
        this.state = state;
        this.collector = collector;
        this.publisher = publisher;
    }

    public boolean synchronize(boolean onlyWhenDue) {
        var run = state.begin(onlyWhenDue);
        if (run.isEmpty()) return false;
        try {
            ReferenceBatch batch = collector.collect(() -> state.reserve(run.get()));
            publisher.publish(run.get(), batch);
            log.info("Reference synchronization completed: version={}, products={}, pairs={}",
                    batch.version(), batch.drugs().size(), batch.interactions().size());
            return true;
        } catch (RuntimeException ex) {
            String code = switch (ex) {
                case MfdsFetchException e -> e.code();
                case ReferenceValidationException e -> e.code();
                case ReferenceSyncException e -> e.code();
                default -> "REFERENCE_SYNC_FAILED";
            };
            state.fail(run.get(), code);
            if (ex instanceof ReferenceValidationException validation) {
                // Collector supplies only catalog IDs/service names or fixed text here, never raw API errors.
                log.warn("Reference validation requires review: code={}, context={}", code, validation.detail());
            }
            // No URLs, credentials, response bodies, or exception cause chains in logs.
            log.warn("Reference synchronization retained last good data: code={}", code);
            throw new ReferenceSyncException(code);
        }
    }
}

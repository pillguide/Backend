package kr.co.pillguide.backend.api.drug.reference;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReferenceSyncService {
    private final ReferenceSyncRepository repository;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final int dailyLimit;
    private final long leaseSeconds;
    private final long refreshDays;

    public ReferenceSyncService(ReferenceSyncRepository repository, JdbcTemplate jdbc, EntityManager em,
            @Value("${reference.sync.daily-request-limit:2000}") int dailyLimit,
            @Value("${reference.sync.lease-seconds:1800}") long leaseSeconds,
            @Value("${reference.sync.refresh-days:7}") long refreshDays) {
        if (dailyLimit < 1 || dailyLimit > 10000 || leaseSeconds < 60 || refreshDays < 1)
            throw new IllegalArgumentException("Invalid reference synchronization bounds");
        this.repository = repository;
        this.jdbc = jdbc;
        this.em = em;
        this.dailyLimit = dailyLimit;
        this.leaseSeconds = leaseSeconds;
        this.refreshDays = refreshDays;
    }

    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> begin(boolean onlyWhenDue) {
        jdbc.update("insert into reference_sync_state (id, requests_used, run_count) values (1, 0, 0) on conflict (id) do nothing");
        ReferenceSyncState state = lock();
        LocalDateTime now = now();
        if (state.getActiveRun() != null && state.getLeaseUntil().isAfter(now)) return Optional.empty();
        if (onlyWhenDue && state.getLastSuccessAt() != null
                && state.getLastSuccessAt().plusDays(refreshDays).isAfter(now)) return Optional.empty();
        // Concurrent replicas and repeated restarts do not immediately retry a failed full collection.
        if (onlyWhenDue && state.getLastAttemptAt() != null
                && state.getLastAttemptAt().plusHours(24).isAfter(now)) return Optional.empty();
        UUID run = UUID.randomUUID();
        state.start(run, now, leaseSeconds);
        return Optional.of(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(UUID runId) {
        ReferenceSyncState state = lockActive(runId);
        state.reserve(now(), dailyLimit, leaseSeconds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReferenceSyncState lockActive(UUID runId) {
        ReferenceSyncState state = lock();
        if (runId == null || !runId.equals(state.getActiveRun()) || state.getLeaseUntil() == null
                || !state.getLeaseUntil().isAfter(now())) throw new ReferenceSyncException("RUN_LEASE_LOST");
        return state;
    }

    private ReferenceSyncState lock() {
        ReferenceSyncState state = repository.lockState()
                .orElseThrow(() -> new ReferenceSyncException("SYNC_STATE_MISSING"));
        em.refresh(state, LockModeType.PESSIMISTIC_WRITE);
        return state;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(ReferenceSyncState state, String version, LocalDate sourceAsOf) {
        state.complete(version, sourceAsOf, now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, String code) {
        ReferenceSyncState state = lock();
        if (runId.equals(state.getActiveRun())) state.fail(code);
    }
}

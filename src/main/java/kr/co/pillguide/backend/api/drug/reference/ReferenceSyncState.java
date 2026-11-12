package kr.co.pillguide.backend.api.drug.reference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One durable checkpoint for the small, complete 150-product reference set. */
@Entity
@Table(name = "reference_sync_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceSyncState {
    @Id private Long id;
    @Column(length = 36) private String currentVersion;
    private LocalDate sourceAsOf;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastAttemptAt;
    @Column(length = 80) private String lastFailureCode;
    private UUID activeRun;
    private LocalDateTime leaseUntil;
    private LocalDate budgetDate;
    @Column(nullable = false) private int requestsUsed;
    @Column(nullable = false) private long runCount;

    void start(UUID run, LocalDateTime now, long leaseSeconds) {
        activeRun = run;
        leaseUntil = now.plusSeconds(leaseSeconds);
        lastAttemptAt = now;
        runCount++;
    }

    void reserve(LocalDateTime now, int limit, long leaseSeconds) {
        if (!now.toLocalDate().equals(budgetDate)) {
            budgetDate = now.toLocalDate();
            requestsUsed = 0;
        }
        if (requestsUsed >= limit) throw new ReferenceSyncException("DAILY_BUDGET_EXHAUSTED");
        requestsUsed++;
        leaseUntil = now.plusSeconds(leaseSeconds);
    }

    void complete(String version, LocalDate date, LocalDateTime now) {
        currentVersion = version;
        sourceAsOf = date;
        lastSuccessAt = now;
        lastFailureCode = null;
        activeRun = null;
        leaseUntil = null;
    }

    void fail(String code) {
        lastFailureCode = code;
        activeRun = null;
        leaseUntil = null;
    }
}

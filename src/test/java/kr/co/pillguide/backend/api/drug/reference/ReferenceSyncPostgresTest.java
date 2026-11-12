package kr.co.pillguide.backend.api.drug.reference;

import kr.co.pillguide.backend.api.member.service.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "reference.sync.daily-request-limit=3",
        "reference.sync.lease-seconds=1800",
        "reference.sync.refresh-days=7"
})
class ReferenceSyncPostgresTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired ReferenceSyncService sync;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    @MockitoBean FcmService fcm;

    @BeforeEach
    void cleanSingletonState() {
        jdbc.update("delete from reference_sync_state where id = 1");
    }

    @Test
    void concurrentBeginAllowsOnlyOneActiveRun() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> beginTogether(ready, start));
            var b = workers.submit(() -> beginTogether(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Optional<UUID> first = a.get(20, TimeUnit.SECONDS);
            Optional<UUID> second = b.get(20, TimeUnit.SECONDS);
            assertEquals(1, (first.isPresent() ? 1 : 0) + (second.isPresent() ? 1 : 0));
            assertEquals(1L, jdbc.queryForObject("select run_count from reference_sync_state where id = 1", Long.class));
        }
    }

    @Test
    void requestReservationsCommitIndividuallyAndOverflowRollsBack() {
        UUID run = sync.begin(false).orElseThrow();
        sync.reserve(run);
        sync.reserve(run);
        sync.reserve(run);
        assertEquals(3, used());
        ReferenceSyncException error = assertThrows(ReferenceSyncException.class, () -> sync.reserve(run));
        assertEquals("DAILY_BUDGET_EXHAUSTED", error.code());
        assertEquals(3, used());
        assertEquals(LocalDate.now(KST), jdbc.queryForObject(
                "select budget_date from reference_sync_state where id = 1", LocalDate.class));
    }

    @Test
    void oldKoreanBudgetDayResetsOnNextReservation() {
        UUID run = sync.begin(false).orElseThrow();
        jdbc.update("update reference_sync_state set budget_date = ?, requests_used = 3 where id = 1",
                LocalDate.now(KST).minusDays(1));
        sync.reserve(run);
        assertEquals(1, used());
        assertEquals(LocalDate.now(KST), jdbc.queryForObject(
                "select budget_date from reference_sync_state where id = 1", LocalDate.class));
    }

    @Test
    void replacementRunCannotBeModifiedByExpiredRun() {
        UUID expired = sync.begin(false).orElseThrow();
        jdbc.update("update reference_sync_state set lease_until = ? where id = 1",
                LocalDateTime.now(KST).minusMinutes(1));
        UUID replacement = sync.begin(false).orElseThrow();
        assertNotEquals(expired, replacement);
        ReferenceSyncException error = assertThrows(ReferenceSyncException.class, () -> sync.reserve(expired));
        assertEquals("RUN_LEASE_LOST", error.code());
        sync.fail(expired, "OLD_RUN_FAILED");
        assertEquals(replacement, activeRun());
        assertNull(jdbc.queryForObject("select last_failure_code from reference_sync_state where id = 1", String.class));
        sync.reserve(replacement);
        assertEquals(1, used());
    }

    @Test
    void failureKeepsCurrentVersionAndLastSuccess() {
        UUID first = sync.begin(false).orElseThrow();
        LocalDate sourceDay = LocalDate.now(KST);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                sync.complete(sync.lockActive(first), "last-good", sourceDay));
        LocalDateTime succeeded = jdbc.queryForObject(
                "select last_success_at from reference_sync_state where id = 1", LocalDateTime.class);
        UUID failing = sync.begin(false).orElseThrow();
        sync.fail(failing, "SOURCE_INCOMPLETE");
        assertEquals("last-good", jdbc.queryForObject(
                "select current_version from reference_sync_state where id = 1", String.class));
        assertEquals(sourceDay, jdbc.queryForObject(
                "select source_as_of from reference_sync_state where id = 1", LocalDate.class));
        assertEquals(succeeded, jdbc.queryForObject(
                "select last_success_at from reference_sync_state where id = 1", LocalDateTime.class));
        assertEquals("SOURCE_INCOMPLETE", jdbc.queryForObject(
                "select last_failure_code from reference_sync_state where id = 1", String.class));
        assertNull(activeRun());
    }

    @Test
    void dueCheckHonorsSevenDaySuccessAndTwentyFourHourAttemptWindows() {
        UUID first = sync.begin(false).orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                sync.complete(sync.lockActive(first), "last-good", LocalDate.now(KST)));
        assertTrue(sync.begin(true).isEmpty());

        LocalDateTime past = LocalDateTime.now(KST);
        jdbc.update("update reference_sync_state set last_success_at = ?, last_attempt_at = ? where id = 1",
                past.minusDays(8), past.minusHours(23));
        assertTrue(sync.begin(true).isEmpty());
        jdbc.update("update reference_sync_state set last_attempt_at = ? where id = 1", past.minusHours(25));
        UUID retry = sync.begin(true).orElseThrow();
        sync.fail(retry, "TEMPORARY_FAILURE");
        assertTrue(sync.begin(true).isEmpty());
    }

    @Test
    void documentedSyncStateSqlCanBeAppliedRepeatedlyWithoutResettingState() throws Exception {
        String sql = Files.readString(Path.of("docs/sql/reference-sync-state.sql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try {
                statement.execute(sql);
                statement.execute("update reference_sync_state set current_version = 'last-good' where id = 1");
                statement.execute(sql);
            } finally {
                // The script uses explicit BEGIN; clear an aborted transaction before pooling this connection.
                statement.execute("ROLLBACK");
            }
        }
        assertEquals(1L, jdbc.queryForObject("select count(*) from reference_sync_state", Long.class));
        assertEquals("last-good", jdbc.queryForObject(
                "select current_version from reference_sync_state where id = 1", String.class));
    }

    private Optional<UUID> beginTogether(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("begin start timed out");
        return sync.begin(false);
    }

    private int used() {
        return jdbc.queryForObject("select requests_used from reference_sync_state where id = 1", Integer.class);
    }

    private UUID activeRun() {
        return jdbc.queryForObject("select active_run from reference_sync_state where id = 1", UUID.class);
    }
}

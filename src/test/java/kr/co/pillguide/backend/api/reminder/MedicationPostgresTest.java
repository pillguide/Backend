package kr.co.pillguide.backend.api.reminder;

import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.member.entity.Member;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("postgres")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class MedicationPostgresTest extends MedicationIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void oldSchemaUpgradePreservesRowsAndUnknownAttempts() throws Exception {
        Long id = due();
        String migration = java.nio.file.Files.readString(java.nio.file.Path.of("docs/sql/pr12-notification-state.sql"));
        jdbc.update("update medication_dose set notification_attempted_at = scheduled_at where id = ?", id);
        jdbc.execute("alter table medication_dose drop column notification_accepted_at, drop column notification_retry_after, drop column notification_stopped_at");
        try {
            jdbc.execute(migration);
            jdbc.execute(migration); // Safe to repeat after a deployment retry.
            var restored = doses.findById(id).orElseThrow();
            assertNotNull(restored.getNotificationAttemptedAt());
            assertNull(restored.getNotificationAcceptedAt());
            assertEquals("medicine", restored.getMedicationName());
            assertEquals(java.util.List.of(id), notifications.prepare(memberId));
            notifications.send(memberId, id);
            assertNotNull(doses.findById(id).orElseThrow().getNotificationAcceptedAt());
        } finally {
            jdbc.execute(migration);
        }
    }

    @Test
    void lockWaitTimesOutAndNextTransactionCanProceed() throws Exception {
        Long id = due();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                medication.lockMember(memberId);
                locked.countDown();
                try { assertTrue(release.await(8, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var waiter = executor.submit(() -> {
                    var transaction = new TransactionTemplate(transactionManager);
                    transaction.setTimeout(1);
                    transaction.executeWithoutResult(status -> notifications.send(memberId, id));
                });
                assertThrows(ExecutionException.class, () -> waiter.get(4, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        }
        verifyNoInteractions(sender);
        notifications.send(memberId, id);
        verify(sender).sendMedicationNotification("test-device", id, "medicine");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"disable", "delete", "check", "logout", "transfer"})
    void committedCancellationWinsAgainstWaitingDispatch(String action) throws Exception {
        Long id = due();
        Long reminderId = medication.day(memberId, today).doses().getFirst().reminderId();
        Long other = members.saveAndFlush(Member.newSocialMember("other", "other@example.invalid", "test", "other")).getId();
        String refreshToken = auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var change = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                switch (action) {
                    case "disable" -> medication.enabled(memberId, reminderId, false);
                    case "delete" -> medication.delete(memberId, java.util.List.of(reminderId));
                    case "check" -> medication.check(memberId, id, true);
                    case "logout" -> auth.logout(refreshToken);
                    case "transfer" -> tokens.register(other, "test-device");
                    default -> throw new AssertionError(action);
                }
                changed.countDown();
                try { assertTrue(commit.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
            }));
            try {
                assertTrue(changed.await(5, TimeUnit.SECONDS));
                var send = executor.submit(() -> notifications.send(memberId, id));
                assertThrows(TimeoutException.class, () -> send.get(200, TimeUnit.MILLISECONDS));
                commit.countDown();
                change.get(5, TimeUnit.SECONDS);
                send.get(5, TimeUnit.SECONDS);
            } finally {
                commit.countDown();
            }
        }
        verifyNoInteractions(sender);
    }

    @Test
    void concurrentTokenClaimsHaveOneOwnerAndDoNotDeadlock() throws Exception {
        Long other = members.saveAndFlush(Member.newSocialMember("other", "other@example.invalid", "test", "other")).getId();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int attempt = 0; attempt < 8; attempt++) {
                String token = "concurrent-device-" + attempt;
                var barrier = new CyclicBarrier(2);
                java.util.concurrent.Callable<Boolean> first = () -> claim(barrier, memberId, token);
                java.util.concurrent.Callable<Boolean> second = () -> claim(barrier, other, token);
                var a = executor.submit(first);
                var b = executor.submit(second);
                boolean aSucceeded = a.get(5, TimeUnit.SECONDS);
                boolean bSucceeded = b.get(5, TimeUnit.SECONDS);
                assertTrue(aSucceeded || bSucceeded);
                assertEquals(1, members.findAll().stream().filter(member -> token.equals(member.getFcmToken())).count());
            }
        }
    }

    private boolean claim(CyclicBarrier barrier, Long owner, String token) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            tokens.register(owner, token);
            return true;
        } catch (kr.co.pillguide.backend.common.exception.ConflictException conflict) {
            return false; // Explicit retryable API conflict; never a 500 or leaked ownership.
        }
    }

    @Test
    void concurrentDispatchesSubmitOnlyOnce() throws Exception {
        Long id = due();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString())).thenAnswer(call -> {
            sending.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new FcmService.SendResult(FcmService.Outcome.ACCEPTED, Duration.ZERO);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> notifications.send(memberId, id));
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            CountDownLatch secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                secondStarted.countDown();
                notifications.send(memberId, id);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        verify(sender, times(1)).sendMedicationNotification(anyString(), anyLong(), anyString());
    }

    @Test
    void revocationWaitsForStartedSubmissionAndBlocksLaterSubmission() throws Exception {
        Long id = due();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString())).thenAnswer(call -> {
            sending.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new FcmService.SendResult(FcmService.Outcome.RETRY, Duration.ZERO);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> notifications.send(memberId, id));
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            var revoke = executor.submit(() -> tokens.delete(memberId));
            assertThrows(TimeoutException.class, () -> revoke.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            revoke.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        notifications.send(memberId, id);
        verify(sender, times(1)).sendMedicationNotification(anyString(), anyLong(), anyString());
        assertNull(members.findById(memberId).orElseThrow().getFcmToken());
    }

    @Test
    void revocationCommittedBeforeDispatchWins() throws Exception {
        Long id = due();
        CountDownLatch revoked = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var revoke = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                tokens.delete(memberId);
                revoked.countDown();
                try {
                    assertTrue(commit.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new RuntimeException(exception);
                }
            }));
            assertTrue(revoked.await(5, TimeUnit.SECONDS));
            var send = executor.submit(() -> notifications.send(memberId, id));
            assertThrows(TimeoutException.class, () -> send.get(200, TimeUnit.MILLISECONDS));
            commit.countDown();
            revoke.get(5, TimeUnit.SECONDS);
            send.get(5, TimeUnit.SECONDS);
        } finally {
            commit.countDown();
        }
        verifyNoInteractions(sender);
    }

    @Test
    void failedTransactionReleasesLocksAndAllowsUncertainRetry() {
        Long id = due();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifications.send(memberId, id);
            status.setRollbackOnly();
        });
        assertEquals(java.util.List.of(id), notifications.prepare(memberId));
        notifications.send(memberId, id);
        verify(sender, times(2)).sendMedicationNotification(anyString(), anyLong(), anyString());
    }

    @Test
    void staleProfileUpdateDoesNotRestoreRevokedToken() throws Exception {
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch revoked = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var profile = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Member member = members.findById(memberId).orElseThrow();
                read.countDown();
                try {
                    assertTrue(revoked.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new RuntimeException(exception);
                }
                member.updateEmail("updated@example.invalid");
            }));
            assertTrue(read.await(5, TimeUnit.SECONDS));
            tokens.delete(memberId);
            revoked.countDown();
            profile.get(5, TimeUnit.SECONDS);
        } finally {
            revoked.countDown();
        }
        assertNull(members.findById(memberId).orElseThrow().getFcmToken());
        assertEquals("updated@example.invalid", members.findById(memberId).orElseThrow().getEmail());
    }
}

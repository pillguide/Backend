package kr.co.pillguide.backend.api.member;

import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class AuthPostgresTest extends AuthIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;

    @Test
    void twoSimultaneousRotationsHaveExactlyOneWinner() throws Exception {
        String original = auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> rotateAfterBarrier(original, ready, start));
            var second = pool.submit(() -> rotateAfterBarrier(original, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            String a = first.get(10, TimeUnit.SECONDS);
            String b = second.get(10, TimeUnit.SECONDS);
            assertEquals(1, (a != null ? 1 : 0) + (b != null ? 1 : 0));
            String winner = a != null ? a : b;
            assertEquals(winner, refreshTokens.findByMemberId(memberId).orElseThrow().getToken());
            assertEquals(1L, jdbc.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class, memberId));
            assertThrows(UnauthorizedException.class, () -> auth.reissue(original));
        }
    }

    @Test
    void committedRotationMakesWaitingLogoutOfOldTokenFail() throws Exception {
        String original = auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
        CountDownLatch rotated = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var rotation = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                String result = auth.reissue(original).refreshToken();
                rotated.countDown();
                await(commit);
                return result;
            }));
            assertTrue(rotated.await(5, TimeUnit.SECONDS));
            var logout = pool.submit(() -> auth.logout(original));
            try {
                assertThrows(TimeoutException.class, () -> logout.get(200, TimeUnit.MILLISECONDS));
            } finally {
                commit.countDown();
            }
            String current = rotation.get(10, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> logout.get(10, TimeUnit.SECONDS));
            assertEquals(current, refreshTokens.findByMemberId(memberId).orElseThrow().getToken());
            assertEquals(0, members.findById(memberId).orElseThrow().getAuthVersion());
        }
    }

    @Test
    void committedLogoutMakesWaitingRotationFailAndClearsFcmAtomically() throws Exception {
        String original = auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                members.findById(memberId).orElseThrow().updateFcmToken("auth-test-device"));
        CountDownLatch revoked = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var logout = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                auth.logout(original);
                revoked.countDown();
                await(commit);
            }));
            assertTrue(revoked.await(5, TimeUnit.SECONDS));
            // The version and FCM change cannot become visible before the refresh deletion commits.
            assertEquals(0L, jdbc.queryForObject("select auth_version from member where id = ?", Long.class, memberId));
            assertEquals("auth-test-device", jdbc.queryForObject("select fcm_token from member where id = ?", String.class, memberId));
            assertEquals(1L, jdbc.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class, memberId));
            var rotation = pool.submit(() -> auth.reissue(original));
            try {
                assertThrows(TimeoutException.class, () -> rotation.get(200, TimeUnit.MILLISECONDS));
            } finally {
                commit.countDown();
            }
            logout.get(10, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> rotation.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1L, jdbc.queryForObject("select auth_version from member where id = ?", Long.class, memberId));
        assertNull(jdbc.queryForObject("select fcm_token from member where id = ?", String.class, memberId));
        assertEquals(0L, jdbc.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class, memberId));
    }

    @Test
    void concurrentLoginsKeepOneRefreshButPreserveEarlierAccess() throws Exception {
        var prior = auth.loginSuccess(members.findById(memberId).orElseThrow());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> loginAfterBarrier(ready, start));
            var second = pool.submit(() -> loginAfterBarrier(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1L, jdbc.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class, memberId));
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/medication/reminders")
                        .header("Authorization", "Bearer " + prior.accessToken()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(0, members.findById(memberId).orElseThrow().getAuthVersion());
    }

    @Test
    void authStateMigrationPreservesLegacyRowsAndRejectsDuplicates() throws Exception {
        String schema = "auth_migration_" + UUID.randomUUID().toString().replace("-", "");
        String migration = Files.readString(Path.of("docs/sql/auth-state.sql"));
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("create schema " + schema);
            try {
                sql.execute("set search_path to " + schema);
                sql.execute("create table member (id bigint primary key, name varchar(50))");
                sql.execute("create table refresh_token (id bigint primary key, member_id bigint, token varchar(200))");
                sql.execute("insert into member values (1, 'preserved')");
                sql.execute("insert into refresh_token values (1, 1, 'legacy-token'), (2, 1, 'duplicate')");
                assertThrows(java.sql.SQLException.class, () -> sql.execute(migration));
                sql.execute("rollback");
                try (ResultSet rows = sql.executeQuery("select count(*) from refresh_token where member_id = 1")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
                sql.execute("delete from refresh_token where id = 2");
                sql.execute(migration);
                sql.execute(migration);
                try (ResultSet rows = sql.executeQuery("select m.name, m.auth_version, r.token from member m join refresh_token r on r.member_id = m.id")) {
                    assertTrue(rows.next());
                    assertEquals("preserved", rows.getString(1));
                    assertEquals(0, rows.getLong(2));
                    assertEquals("legacy-token", rows.getString(3));
                    assertFalse(rows.next());
                }
                sql.execute("insert into member (id, name) values (2, 'new')");
                try (ResultSet rows = sql.executeQuery("select auth_version from member where id = 2")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getLong(1));
                }
                assertThrows(java.sql.SQLException.class, () -> sql.execute("insert into refresh_token values (3, 1, 'second')"));
            } finally {
                sql.execute("set search_path to public");
                sql.execute("drop schema " + schema + " cascade");
            }
        }
    }

    private String rotateAfterBarrier(String token, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try { return auth.reissue(token).refreshToken(); }
        catch (UnauthorizedException expected) { return null; }
    }

    private String loginAfterBarrier(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        return auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(8, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}

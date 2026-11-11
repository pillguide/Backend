package kr.co.pillguide.backend.api.member;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.member.service.LoginExchangeCleanup;
import kr.co.pillguide.backend.api.member.service.LoginExchangeService;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "app.oauth2.exchange-cleanup-initial-delay-ms=3600000"
})
class LoginExchangePostgresTest {
    @Autowired MemberRepository members;
    @Autowired LoginExchangeService exchanges;
    @Autowired LoginExchangeCleanup cleanup;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext webContext;
    @MockitoBean FcmService fcm;

    Long memberId;

    @BeforeEach
    void setUp() {
        memberId = members.saveAndFlush(Member.newSocialMember(
                "exchange-test", "exchange@example.invalid", "test", UUID.randomUUID().toString())).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from login_exchange where member_id = ?", memberId);
    }

    @Test
    void wrongVerifierPreservesCodeThenHttpExchangeSucceedsOnce() throws Exception {
        String verifier = "V".repeat(43);
        String code = exchanges.issueCode(memberId, challenge(verifier));
        MockMvc mvc = mvc();

        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType("application/json").content(body(code, "W".repeat(43))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        assertEquals(1L, count("login_exchange"));
        assertEquals(0L, count("refresh_token"));

        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType("application/json").content(body(code, verifier)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
        assertEquals(0L, count("login_exchange"));
        assertEquals(1L, count("refresh_token"));
        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType("application/json").content(body(code, verifier)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void simultaneousExchangeHasOneWinner() throws Exception {
        String verifier = "V".repeat(43);
        String code = exchanges.issueCode(memberId, challenge(verifier));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> exchangeAfterBarrier(code, verifier, ready, start));
            var second = pool.submit(() -> exchangeAfterBarrier(code, verifier, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        }
        assertEquals(0L, count("login_exchange"));
        assertEquals(1L, count("refresh_token"));
    }

    @Test
    void failedOuterTransactionRollsBackConsumptionAndTokenIssuance() {
        String verifier = "V".repeat(43);
        String code = exchanges.issueCode(memberId, challenge(verifier));

        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    exchanges.exchange(code, verifier);
                    throw new IllegalStateException("force rollback");
                }));
        assertEquals(1L, count("login_exchange"));
        assertEquals(0L, count("refresh_token"));
        exchanges.exchange(code, verifier);
        assertEquals(0L, count("login_exchange"));
        assertEquals(1L, count("refresh_token"));
    }

    @Test
    void cleanupDeletesOnlyExpiredRowsInsideItsTransaction() {
        String expired = exchanges.issueCode(memberId, challenge("V".repeat(43)));
        jdbc.update("update login_exchange set expires_at = ? where member_id = ?",
                LocalDateTime.now().minusMinutes(1), memberId);
        String valid = exchanges.issueCode(memberId, challenge("W".repeat(43)));
        cleanup.deleteExpired();
        assertEquals(1L, count("login_exchange"));
        assertThrows(UnauthorizedException.class, () -> exchanges.exchange(expired, "V".repeat(43)));
        exchanges.exchange(valid, "W".repeat(43));
    }

    @Test
    void migrationCanBeReappliedWithoutErasingRows() throws Exception {
        String schema = "exchange_migration_" + UUID.randomUUID().toString().replace("-", "");
        String migration = Files.readString(Path.of("docs/sql/login-exchange.sql"));
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("create schema " + schema);
            try {
                sql.execute("set search_path to " + schema);
                sql.execute("create table member (id bigint primary key)");
                sql.execute("insert into member values (1)");
                sql.execute(migration);
                sql.execute("insert into login_exchange (code_hash, challenge, member_id, expires_at) "
                        + "values ('preserved-hash', '" + "A".repeat(43) + "', 1, now())");
                sql.execute(migration);
                try (var rows = sql.executeQuery("select count(*) from login_exchange where code_hash = 'preserved-hash'")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
            } finally {
                sql.execute("set search_path to public");
                sql.execute("drop schema " + schema + " cascade");
            }
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    }

    private Long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where member_id = ?", Long.class, memberId);
    }

    private boolean exchangeAfterBarrier(String code, String verifier, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            assertTrue(start.await(8, TimeUnit.SECONDS));
            exchanges.exchange(code, verifier);
            return true;
        } catch (UnauthorizedException expected) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static String body(String code, String verifier) {
        return "{\"code\":\"" + code + "\",\"verifier\":\"" + verifier + "\"}";
    }

    private static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

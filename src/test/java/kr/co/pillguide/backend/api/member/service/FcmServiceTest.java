package kr.co.pillguide.backend.api.member.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FcmServiceTest {
    HttpServer server;
    ExecutorService executor;
    FcmService sender;
    volatile int status = 200;
    volatile String body = "{\"name\":\"projects/test/messages/1\"}";
    volatile String retryAfter;
    volatile String receivedBody;
    volatile String authorization;
    volatile long delayMillis;
    AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/send", exchange -> {
            requests.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (retryAfter != null) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try {
                Thread.sleep(delayMillis);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken("test-access-token", Date.from(Instant.now().plusSeconds(3600))));
        sender = new FcmService(credentials, HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/send"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void preservesNotificationAndDataPayload() {
        assertEquals(FcmService.Outcome.ACCEPTED, sender.sendMedicationNotification("device", 42L, "약 이름").outcome());
        var message = JsonParser.parseString(receivedBody).getAsJsonObject().getAsJsonObject("message");
        assertEquals("device", message.get("token").getAsString());
        assertEquals("42", message.getAsJsonObject("data").get("doseId").getAsString());
        assertEquals("MEDICATION_REMINDER", message.getAsJsonObject("data").get("type").getAsString());
        assertEquals("약 이름 복용 시간이에요.", message.getAsJsonObject("notification").get("body").getAsString());
        assertEquals("Bearer test-access-token", authorization);
    }

    @Test
    void retryAfterIsReturnedWithoutHiddenHttpRetries() {
        status = 503;
        retryAfter = "120";
        var result = sender.sendMedicationNotification("device", 42L, "medicine");
        assertEquals(FcmService.Outcome.RETRY, result.outcome());
        assertEquals(Duration.ofSeconds(120), result.retryAfter());
        assertEquals(1, requests.get());
    }

    @Test
    void quotaErrorWaitsAtLeastOneMinute() {
        status = 429;
        retryAfter = "bad-date";
        assertEquals(Duration.ofMinutes(1), sender.sendMedicationNotification("device", 42L, "medicine").retryAfter());
    }

    @Test
    void unregisteredAndOtherPermanentErrorsAreDistinct() {
        status = 404;
        body = "{\"error\":{\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}";
        assertEquals(FcmService.Outcome.INVALID_TOKEN, sender.sendMedicationNotification("device", 42L, "medicine").outcome());
        status = 400;
        body = "invalid upstream body";
        assertEquals(FcmService.Outcome.PERMANENT_FAILURE, sender.sendMedicationNotification("device", 42L, "medicine").outcome());
    }

    @Test
    void slowResponseBodyDoesNotHoldDatabaseWorkIndefinitely() {
        delayMillis = 8000;
        assertTimeoutPreemptively(Duration.ofSeconds(7), () -> {
            assertEquals(FcmService.Outcome.RETRY, sender.sendMedicationNotification("device", 42L, "medicine").outcome());
        });
    }
}

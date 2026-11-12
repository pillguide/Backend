package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfdsClientTest {
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> paths = new ArrayList<>();
    private volatile Consumer<HttpExchange> response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/1471000/", exchange -> {
            requests.incrementAndGet();
            synchronized (paths) { paths.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery()); }
            response.accept(exchange);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void paginatesExactlyAndChargesEveryPage() {
        response = exchange -> {
            boolean second = exchange.getRequestURI().getRawQuery().contains("pageNo=2");
            send(exchange, 200, payload(101, second ? "{\"itemSeq\":\"123\",\"itemName\":\"last\"}"
                    : hundredRows("itemSeq", "123")));
        };
        AtomicInteger charged = new AtomicInteger();
        var data = client(2_000).fetch(MfdsClient.Service.EASY, "123", charged::incrementAndGet);
        assertThat(data.records()).hasSize(101);
        assertThat(data.queryKey()).isEqualTo("123");
        assertThat(charged.get()).isEqualTo(2);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(paths.get(0)).contains("DrbEasyDrugInfoService/getDrbEasyDrugList", "ServiceKey=hidden-key", "itemSeq=123", "numOfRows=100");
    }

    @Test
    void callbackFailureStopsBeforeHttpAndMissingKeyIsLocal() {
        response = exchange -> send(exchange, 200, payload(0, "null"));
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.EASY, "123",
                () -> { throw new IllegalStateException("budget exhausted"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("budget exhausted");
        assertThat(requests.get()).isZero();
        MfdsProperties noKey = new MfdsProperties(baseUrl(), "", "", "", "", 500, 2_000, 1024 * 1024, 0, 0);
        assertThatThrownBy(() -> new MfdsClient(noKey, new ObjectMapper()).fetch(MfdsClient.Service.EASY, "123", () -> {}))
                .isInstanceOf(MfdsFetchException.class).hasMessage("MISSING_KEY");
        assertThat(requests.get()).isZero();
    }

    @Test
    void encodesReservedCharactersInCredential() {
        response = exchange -> send(exchange, 200, payload(0, "null"));
        var settings = new MfdsProperties(baseUrl(), "x+y/z=", "", "", "", 500, 2_000,
                1024 * 1024, 0, 0);
        new MfdsClient(settings, new ObjectMapper()).fetch(MfdsClient.Service.EASY, "123", () -> {});
        assertThat(paths.get(0)).contains("ServiceKey=x%2By%2Fz%3D");
    }

    @Test
    void retriesQuotaWithCallbackPerAttemptAndNeverExposesKey() {
        response = exchange -> {
            if (requests.get() <= 2) {
                exchange.getResponseHeaders().set("Retry-After", "not-a-date");
                send(exchange, 429, "secret body hidden-key");
            } else send(exchange, 200, payload(0, "null"));
        };
        AtomicInteger charged = new AtomicInteger();
        var data = client(2_000).fetch(MfdsClient.Service.DUR_INGREDIENT, null, charged::incrementAndGet);
        assertThat(data.queryKey()).isEqualTo("ALL");
        assertThat(data.records()).isEmpty();
        assertThat(charged.get()).isEqualTo(3);
        assertThat(requests.get()).isEqualTo(3);
        assertThat(paths.get(0)).contains("DURIrdntInfoService03/getUsjntTabooInfoList02");
        assertThat(paths.get(0)).doesNotContain("itemSeq=");
        response = exchange -> send(exchange, 400, "secret body hidden-key");
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.EASY, "123", () -> {}))
                .isInstanceOf(MfdsFetchException.class)
                .hasMessage("HTTP_STATUS")
                .satisfies(error -> {
                    assertThat(((MfdsFetchException) error).status()).isEqualTo(400);
                    assertThat(error.toString()).doesNotContain("hidden-key", "secret body", "http://");
                });
        assertThat(requests.get()).isEqualTo(4);
    }

    @Test
    void longRetryAfterAbortsBeforeAnotherBudgetChargeFor429And503() {
        response = exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "3");
            send(exchange, requests.get() == 1 ? 429 : 503, "hidden-key is never retained");
        };
        AtomicInteger charged = new AtomicInteger();
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.EASY, "123", charged::incrementAndGet))
                .isInstanceOf(MfdsFetchException.class).hasMessage("RATE_LIMIT_WAIT_REQUIRED")
                .satisfies(error -> assertThat(((MfdsFetchException) error).status()).isEqualTo(429));
        assertThat(charged.get()).isEqualTo(1);
        assertThat(requests.get()).isEqualTo(1);
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.EASY, "123", charged::incrementAndGet))
                .isInstanceOf(MfdsFetchException.class).hasMessage("RATE_LIMIT_WAIT_REQUIRED")
                .satisfies(error -> assertThat(((MfdsFetchException) error).status()).isEqualTo(503));
        assertThat(charged.get()).isEqualTo(2);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void retryAfterWithinBoundIsWaitedBeforeRetry() {
        response = exchange -> {
            if (requests.get() == 1) {
                exchange.getResponseHeaders().set("Retry-After", "1");
                send(exchange, 429, "limited");
            } else send(exchange, 200, payload(0, "null"));
        };
        AtomicInteger charged = new AtomicInteger();
        long started = System.nanoTime();
        client(2_000).fetch(MfdsClient.Service.EASY, "123", charged::incrementAndGet);
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(charged.get()).isEqualTo(2);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(950);
    }

    @Test
    void rejectsIncompleteAndMismatchedPages() {
        response = exchange -> send(exchange, 200, payload(2, "{\"ITEM_SEQ\":\"123\"}"));
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.PERMIT_DETAIL, "123", () -> {}))
                .isInstanceOf(MfdsFetchException.class).hasMessage("REPEATED_PAGE");
        response = exchange -> send(exchange, 200, payload(1, "{\"ITEM_SEQ\":\"999\"}"));
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.DUR_PRODUCT, "123", () -> {}))
                .isInstanceOf(MfdsFetchException.class).hasMessage("ITEM_SEQ_MISMATCH");
        response = exchange -> send(exchange, 200, payload(1, "null"));
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.PERMIT_INGREDIENTS, "123", () -> {}))
                .isInstanceOf(MfdsFetchException.class).hasMessage("EARLY_EMPTY_PAGE");
    }

    @Test
    void acceptsNestedItemShapeAndRejectsChangedTotal() {
        response = exchange -> send(exchange, 200, "{\"response\":{\"header\":{\"resultCode\":\"0000\"},"
                + "\"body\":{\"totalCount\":1,\"items\":{\"item\":{\"ITEM_SEQ\":\"123\"}}}}}");
        assertThat(client(2_000).fetch(MfdsClient.Service.PERMIT_LIST, "123", () -> {}).records()).hasSize(1);
        response = exchange -> send(exchange, 200, payload(requests.get() == 2 ? 2 : 3,
                "{\"itemSeq\":\"123\"}"));
        assertThatThrownBy(() -> client(2_000).fetch(MfdsClient.Service.EASY, "123", () -> {}))
                .isInstanceOf(MfdsFetchException.class).hasMessage("TOTAL_CHANGED");
    }

    @Test
    void timesOutSlowBodyWithBoundedRetries() {
        response = exchange -> {
            try { Thread.sleep(150); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            send(exchange, 200, payload(0, "null"));
        };
        AtomicInteger charged = new AtomicInteger();
        assertThatThrownBy(() -> client(40).fetch(MfdsClient.Service.EASY, "123", charged::incrementAndGet))
                .isInstanceOf(MfdsFetchException.class).hasMessage("HTTP_TIMEOUT");
        assertThat(charged.get()).isEqualTo(3);
    }

    private MfdsClient client(long timeoutMillis) {
        var settings = new MfdsProperties(baseUrl(), "hidden-key", "hidden-key", "hidden-key", "hidden-key",
                500, timeoutMillis, 1024 * 1024, 0, 2_000);
        return new MfdsClient(settings, new ObjectMapper());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/1471000";
    }

    private static String payload(int total, String items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":"
                + total + ",\"items\":" + items + "}}}";
    }

    private static String hundredRows(String field, String value) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) rows.add("{\"" + field + "\":\"" + value + "\",\"row\":" + i + "}");
        return "[" + String.join(",", rows) + "]";
    }

    private static void send(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        } catch (IOException ignored) {
            exchange.close();
        }
    }
}

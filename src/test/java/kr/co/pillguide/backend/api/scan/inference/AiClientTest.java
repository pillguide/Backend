package kr.co.pillguide.backend.api.scan.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import kr.co.pillguide.backend.api.drug.reference.SupportedDrugCatalog;
import kr.co.pillguide.backend.api.scan.exception.ScanException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientTest {
    private static final String MODEL = "fb11caa45b8f80e077aaa12072915dccca27e704b7f3ecffa266370a3fb500b7";
    private static final String MAPPING = "71569e7f4d277b5c6e69930086db7d52920a1c12dae3325a9cbc2234829e8bce";
    private final SupportedDrugCatalog catalog = new SupportedDrugCatalog();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private volatile Consumer<HttpExchange> response;
    private volatile String requestType;
    private volatile String requestKey;
    private volatile String requestBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            requests.incrementAndGet();
            requestType = exchange.getRequestHeaders().getFirst("Content-Type");
            requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            response.accept(exchange);
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void sendsOneMultipartRequestAndPreservesRepeatedDetections() {
        String itemSeq = catalog.byClassId(0).orElseThrow().itemSeq();
        response = exchange -> send(exchange, 200, success("[" + prediction(1, 0, itemSeq, "0.95", "[0.1,0.2,0.3,0.4]")
                + "," + prediction(2, 0, itemSeq, "0.7", "[0.5,0.6,0.8,0.9]") + "]"));
        AiPredictionResult result = client(2000).predict(input());
        assertThat(result.predictions()).hasSize(2);
        assertThat(result.predictions().get(0).itemSeq()).isEqualTo(itemSeq);
        assertThat(result.predictions().get(1).itemSeq()).isEqualTo(itemSeq);
        assertThat(result.predictions().get(1).detectionIndex()).isEqualTo(2);
        assertThat(requests.get()).isEqualTo(1);
        assertThat(requestType).startsWith("multipart/form-data;").contains("boundary=");
        assertThat(requestKey).isEqualTo("test-secret");
        assertThat(requestBody).contains("name=\"file\"", "filename=\"upload.jpg\"", "Content-Type: image/jpeg");
        assertThat(requestBody).doesNotContain("private-name");
    }

    @Test
    void acceptsEmptyPredictionButRejectsContractDrift() {
        response = exchange -> send(exchange, 200, success("[]"));
        assertThat(client(2000).predict(input()).predictions()).isEmpty();
        String itemSeq = catalog.byClassId(0).orElseThrow().itemSeq();
        for (String invalid : new String[] {
                success("[" + prediction(2, 0, itemSeq, "0.9", "[0.1,0.2,0.3,0.4]") + "]"),
                success("[" + prediction(1, 0, "999999999", "0.9", "[0.1,0.2,0.3,0.4]") + "]"),
                success("[" + prediction(1, 0, itemSeq, "1.2", "[0.1,0.2,0.3,0.4]") + "]"),
                success("[" + prediction(1, 0, itemSeq, "0.9", "[0.4,0.2,0.3,0.4]") + "]"),
                success("[]").replace(MAPPING, "b".repeat(64)),
                success("[]").replace("\"width\":3", "\"width\":4")
        }) {
            response = exchange -> send(exchange, 200, invalid);
            assertStatus(() -> client(2000).predict(input()), HttpStatus.BAD_GATEWAY);
        }
    }

    @Test
    void rejectsContradictoryDuplicateJsonKeys() {
        response = exchange -> send(exchange, 200, success("[]").replace("\"predictions\":[]",
                "\"predictions\":[{\"invalid\":true}],\"predictions\":[]"));
        assertStatus(() -> client(2000).predict(input()), HttpStatus.BAD_GATEWAY);
        String seq = catalog.byClassId(0).orElseThrow().itemSeq();
        String duplicated = prediction(1, 0, seq, "0.9", "[0.1,0.2,0.3,0.4]")
                .replace("\"item_seq\":", "\"item_seq\":\"999999999\",\"item_seq\":");
        response = exchange -> send(exchange, 200, success("[" + duplicated + "]"));
        assertStatus(() -> client(2000).predict(input()), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mapsBusyAndUpstreamFailuresWithoutBodyOrKeyLeakAndNeverRetries() {
        response = exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "1");
            send(exchange, 503, "upstream detail test-secret");
        };
        assertThatThrownBy(() -> client(2000).predict(input())).isInstanceOf(ScanException.class)
                .satisfies(error -> {
                    ScanException scan = (ScanException) error;
                    assertThat(scan.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(scan.retryAfterSeconds()).isEqualTo(1);
                    assertThat(scan.toString()).doesNotContain("test-secret", "upstream detail", "http://");
                });
        assertThat(requests.get()).isEqualTo(1);
        response = exchange -> send(exchange, 401, "invalid key test-secret");
        assertStatus(() -> client(2000).predict(input()), HttpStatus.BAD_GATEWAY);
        assertThat(requests.get()).isEqualTo(2);
        response = exchange -> send(exchange, 415, "invalid image detail");
        assertStatus(() -> client(2000).predict(input()), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void rejectsUnconfiguredClientWithoutRequestAndTimesOutSlowHeadersOnce() {
        AiProperties unset = new AiProperties("", "", MODEL, MAPPING, 500, 100, 1024 * 1024);
        assertStatus(() -> new AiClient(unset, catalog, new ObjectMapper()).predict(input()),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(requests.get()).isZero();
        response = exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            send(exchange, 200, success("[]"));
        };
        assertStatus(() -> client(40).predict(input()), HttpStatus.GATEWAY_TIMEOUT);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void timesOutAfterHeadersWhileBodyIsStillPendingWithoutRetry() throws Exception {
        CountDownLatch headersSent = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        response = exchange -> {
            try {
                byte[] bytes = success("[]").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().flush();
                headersSent.countDown();
                if (releaseBody.await(5, TimeUnit.SECONDS)) {
                    try (var out = exchange.getResponseBody()) { out.write(bytes); }
                }
            } catch (IOException ignored) { exchange.close(); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); exchange.close(); }
        };
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> {
                try { client(1000).predict(input()); return (RuntimeException) null; }
                catch (RuntimeException error) { return error; }
            });
            assertThat(headersSent.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(ScanException.class)
                    .satisfies(error -> assertThat(((ScanException) error).status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        } finally { releaseBody.countDown(); }
        assertThat(requests.get()).isEqualTo(1);
    }

    private AiClient client(long timeout) {
        AiProperties settings = new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-secret", MODEL, MAPPING, 500, timeout, 1024 * 1024);
        return new AiClient(settings, catalog, new ObjectMapper());
    }

    private static PhotoInput input() {
        return new PhotoInput(new byte[] {1, 2, 3}, "image/jpeg", "a".repeat(64), 3, 2);
    }

    private static String success(String predictions) {
        return "{\"model\":\"H\",\"model_sha256\":\"" + MODEL + "\",\"mapping_sha256\":\"" + MAPPING
                + "\",\"image\":{\"width\":3,\"height\":2},\"predictions\":" + predictions + "}";
    }

    private static String prediction(int id, int cls, String seq, String confidence, String bbox) {
        return "{\"detection_id\":" + id + ",\"class_id\":" + cls + ",\"item_seq\":\"" + seq
                + "\",\"confidence\":" + confidence + ",\"bbox_xyxy\":" + bbox
                + ",\"recognition_may_be_degraded\":false}";
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOf(ScanException.class)
                .satisfies(error -> assertThat(((ScanException) error).status()).isEqualTo(status));
    }

    private static void send(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        } catch (IOException ignored) { exchange.close(); }
    }
}

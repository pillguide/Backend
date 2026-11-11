package kr.co.pillguide.backend.api.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.drug.reference.ApprovedInteractionPairs;
import kr.co.pillguide.backend.api.drug.reference.ReferenceBatch;
import kr.co.pillguide.backend.api.drug.reference.ReferencePublisher;
import kr.co.pillguide.backend.api.drug.reference.ReferenceSyncService;
import kr.co.pillguide.backend.api.drug.reference.SupportedDrugCatalog;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit live integration gate: real loopback HTTP/AI and isolated PG, controlled reference fixture. */
@Tag("ai-live")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "reference.sync.enabled=false",
        "scan.ai.base-url=${AI_LIVE_BASE_URL}",
        "scan.ai.api-key=${AI_LIVE_API_KEY}",
        "spring.servlet.multipart.location=${AI_LIVE_UPLOAD_TMP}"
})
class ScanLiveTest {
    private static final String HASH = "c".repeat(64);
    private static final String[] GROUPS = {"single", "native", "synthetic"};
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper;
    @Autowired SupportedDrugCatalog catalog;
    @Autowired ReferenceSyncService sync;
    @Autowired ReferencePublisher publisher;
    @Autowired MemberRepository members;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FcmService fcm;

    @Test
    void liveInferenceAndSpringScanPreserveDetectionsAndHistory() throws Exception {
        assertEquals("true", System.getenv("AI_LIVE_TEST"));
        assertEquals("jdbc:postgresql://127.0.0.1:55432/pillguide_test", System.getenv("PG_TEST_URL"));
        assertEquals("http://127.0.0.1:8000", System.getenv("AI_LIVE_BASE_URL"));
        Path sampleRoot = Path.of(required("AI_LIVE_SAMPLE_ROOT")).toAbsolutePath().normalize();
        Path uploadTmp = Path.of(required("AI_LIVE_UPLOAD_TMP"));
        assertTrue(Files.isDirectory(uploadTmp));
        assertEmpty(uploadTmp);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("referenceSource", "controlled");
        report.put("referenceScope", "Synthetic 150-item fixture and four reviewed pair identities; no live MFDS collection or accuracy claim");
        report.put("aiSource", "local FastAPI HTTP");
        List<Map<String, Object>> samples = new ArrayList<>();
        report.put("samples", samples);
        try {
            String version = publishControlledReference();
            report.put("referenceVersion", version);
            Member member = members.saveAndFlush(Member.newSocialMember("AI live test", null,
                    "ai-live", UUID.randomUUID().toString()));
            var tokens = auth.loginSuccess(member);
            String access = tokens.accessToken();
            String refresh = tokens.refreshToken();
            int detections = 0;
            JsonNode selectedScan = null;
            for (String group : GROUPS) {
                for (int index = 1; index <= 2; index++) {
                    String label = group + "_val_0" + index;
                    Path path = sampleRoot.resolve("samples").resolve(group).resolve("images")
                            .resolve(label + ".jpg").normalize();
                    assertTrue(path.startsWith(sampleRoot) && Files.isRegularFile(path), label);
                    byte[] bytes = Files.readAllBytes(path);
                    JsonNode direct = json(directAi(bytes, "image/jpeg"), 200);
                    UUID requestId = UUID.randomUUID();
                    HttpResponse<String> created = scan(bytes, "image/jpeg", requestId, access);
                    JsonNode saved = json(created, 201).path("data");
                    comparePrediction(direct, saved, requestId, version);
                    detections += saved.path("drugCount").asInt();
                    if (selectedScan == null && saved.path("drugCount").asInt() > 0) selectedScan = saved;
                    long historyId = saved.path("sessionId").asLong();
                    assertTrue(historyId > 0);
                    assertEquals(1, jdbc.queryForObject("select count(*) from scan_session where id = ? and member_id = ?",
                            Integer.class, historyId, member.getId()));
                    JsonNode replay = json(scan(bytes, "image/jpeg", requestId, access), 200).path("data");
                    assertEquals(historyId, replay.path("sessionId").asLong());
                    assertEquals(saved, replay);
                    JsonNode history = json(get("/api/v1/scan/" + historyId, access), 200).path("data");
                    assertEquals(saved, history);
                    assertEmpty(uploadTmp);
                    samples.add(Map.of("sample", label, "directDetections", direct.path("predictions").size(),
                            "persistedDetections", saved.path("drugCount").asInt(), "parity", "PASSED"));
                }
            }
            report.put("sampleDetections", detections);
            assertNotNull(selectedScan, "At least one real AI detection is needed for the My Drug HTTP check");
            long selectedSessionId = selectedScan.path("sessionId").asLong();
            JsonNode selectedDetection = selectedScan.path("drugs").get(0);
            long detectionId = selectedDetection.path("detectionId").asLong();
            assertTrue(detectionId > 0);
            String choice = mapper.writeValueAsString(Map.of(
                    "scanSessionId", selectedSessionId, "detectionIds", List.of(detectionId)));
            JsonNode firstSelection = json(postJson("/api/v1/my-drugs", choice, access), 201).path("data");
            assertEquals(1, firstSelection.size());
            JsonNode firstMyDrug = firstSelection.get(0);
            long memberDrugId = firstMyDrug.path("memberDrugId").asLong();
            String firstRegisteredAt = firstMyDrug.path("firstRegisteredAt").asText();
            assertTrue(memberDrugId > 0);
            assertEquals(selectedDetection.path("code").asText(), firstMyDrug.path("code").asText());
            JsonNode repeatedSelection = json(postJson("/api/v1/my-drugs", choice, access), 200).path("data");
            assertEquals(1, repeatedSelection.size());
            JsonNode repeatedMyDrug = repeatedSelection.get(0);
            assertEquals(memberDrugId, repeatedMyDrug.path("memberDrugId").asLong());
            assertEquals(firstRegisteredAt, repeatedMyDrug.path("firstRegisteredAt").asText());
            JsonNode myDrugPage = json(get("/api/v1/my-drugs?page=0&size=20", access), 200).path("data");
            assertEquals(1, myDrugPage.path("totalElements").asInt());
            assertEquals(memberDrugId, myDrugPage.path("items").get(0).path("memberDrugId").asLong());
            JsonNode myDrugDetail = json(get("/api/v1/my-drugs/" + memberDrugId, access), 200).path("data");
            assertEquals(firstRegisteredAt, myDrugDetail.path("firstRegisteredAt").asText());
            assertEquals(selectedDetection.path("code").asText(), myDrugDetail.path("drug").path("reference").path("code").asText());
            assertEquals(memberDrugId, json(delete("/api/v1/my-drugs/" + memberDrugId, access), 200).path("data").asLong());
            assertEquals(404, get("/api/v1/my-drugs/" + memberDrugId, access).statusCode());
            assertEquals(selectedScan, json(get("/api/v1/scan/" + selectedSessionId, access), 200).path("data"));
            report.put("myDrugSource", "selected real AI detection with controlled reference fixture");
            report.put("myDrugHttpChecks", "201 add, 200 duplicate, GET page/detail, 200 delete, scan retained");
            assertEmpty(uploadTmp);
            byte[] white = whitePng();
            JsonNode directWhite = json(directAi(white, "image/png"), 200);
            JsonNode savedWhite = json(scan(white, "image/png", UUID.randomUUID(), access), 201).path("data");
            assertEquals(directWhite.path("predictions").size(), savedWhite.path("drugCount").asInt());
            if (directWhite.path("predictions").isEmpty()) assertEquals(0, savedWhite.path("drugs").size());
            report.put("whitePngDirectDetections", directWhite.path("predictions").size());
            report.put("whitePngBackendDetections", savedWhite.path("drugCount").asInt());

            assertEquals(401, scan(white, "image/png", UUID.randomUUID(), null).statusCode());
            assertEquals(401, scan(white, "image/png", UUID.randomUUID(), refresh).statusCode());
            assertEquals(415, scan("not-an-image".getBytes(StandardCharsets.UTF_8),
                    "image/jpeg", UUID.randomUUID(), access).statusCode());
            assertEquals(413, scan(new byte[10 * 1024 * 1024 + 1],
                    "image/jpeg", UUID.randomUUID(), access).statusCode());
            JsonNode docs = json(get("/v3/api-docs", null), 200);
            JsonNode operation = docs.path("paths").path("/api/v1/scan").path("post");
            assertTrue(operation.path("requestBody").path("content").has("multipart/form-data"));
            for (String status : List.of("201", "200", "400", "401", "409", "413", "415", "500", "502", "503", "504")) {
                assertTrue(operation.path("responses").has(status), "OpenAPI " + status);
            }
            assertEmpty(uploadTmp);
            report.put("httpChecks", "201, 200 replay, GET history, 401 unauthenticated and refresh, 415, 413, OpenAPI 200");
            report.put("uploadTmpEmpty", true);
            report.put("outcome", "PASSED");
        } catch (Throwable failure) {
            report.put("outcome", "FAILED");
            report.put("failureType", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            Files.writeString(Path.of(required("AI_LIVE_REPORT")),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }

    private String publishControlledReference() {
        String version = UUID.randomUUID().toString();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        var rows = catalog.all().stream().map(d -> new ReferenceBatch.DrugReference(
                d.itemSeq(), d.modelName(), d.productCode(), d.modelName(), null, null, null,
                "CONTROLLED", "CONTROLLED", null, null, null, null,
                "{\"source\":\"controlled-ai-live-test\"}", now)).toList();
        var pairs = ApprovedInteractionPairs.ALL.stream().map(p -> new ReferenceBatch.InteractionReference(
                p.firstItemSeq(), p.secondItemSeq(), "[{\"source\":\"controlled-ai-live-test\"}]",
                null, "Controlled fixture pair; no official medical claim", HASH, HASH)).toList();
        publisher.publish(sync.begin(false).orElseThrow(), new ReferenceBatch(version, today, rows, pairs));
        return version;
    }

    private void comparePrediction(JsonNode direct, JsonNode saved, UUID requestId, String version) {
        assertEquals(requestId.toString(), saved.path("requestId").asText());
        assertEquals(version, saved.path("referenceVersion").asText());
        assertEquals(direct.path("image").path("width").asInt(), saved.path("imageSize").path("width").asInt());
        assertEquals(direct.path("image").path("height").asInt(), saved.path("imageSize").path("height").asInt());
        assertEquals(direct.path("model_sha256").asText(), saved.path("modelSha256").asText());
        assertEquals(direct.path("mapping_sha256").asText(), saved.path("mappingSha256").asText());
        JsonNode predictions = direct.path("predictions");
        JsonNode drugs = saved.path("drugs");
        assertEquals(predictions.size(), saved.path("drugCount").asInt());
        assertEquals(predictions.size(), drugs.size());
        for (int i = 0; i < predictions.size(); i++) {
            JsonNode expected = predictions.get(i);
            JsonNode actual = drugs.get(i);
            assertEquals(expected.path("detection_id").asInt(), actual.path("detectionIndex").asInt());
            assertEquals(expected.path("item_seq").asText(), actual.path("code").asText());
            assertEquals(expected.path("confidence").asDouble(), actual.path("confidence").asDouble(), 0.001);
            for (int j = 0; j < 4; j++) assertEquals(expected.path("bbox_xyxy").get(j).asDouble(),
                    actual.path("bboxXyxy").get(j).asDouble(), 0.001);
            assertEquals(expected.path("recognition_may_be_degraded").asBoolean(),
                    actual.path("recognitionMayBeDegraded").asBoolean());
        }
    }

    private HttpResponse<String> directAi(byte[] bytes, String mediaType) throws Exception {
        String boundary = "ai-live-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(URI.create(required("AI_LIVE_BASE_URL") + "/predict"))
                .timeout(Duration.ofSeconds(90)).header("X-API-Key", required("AI_LIVE_API_KEY"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(bytes, mediaType, boundary, null))).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> scan(byte[] bytes, String mediaType, UUID requestId, String bearer) throws Exception {
        String boundary = "scan-live-" + UUID.randomUUID();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/scan"))
                .timeout(Duration.ofSeconds(90)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(bytes, mediaType, boundary, requestId.toString())));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String bearer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String bearer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + bearer).DELETE().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response, int status) throws IOException {
        assertEquals(status, response.statusCode());
        return mapper.readTree(response.body());
    }

    private static byte[] multipart(byte[] bytes, String mediaType, String boundary, String requestId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample\"\r\n"
                + "Content-Type: " + mediaType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        if (requestId != null) {
            out.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"
                    + requestId).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static byte[] whitePng() throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 256, 256); }
        finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " is required");
        assertFalse(value.isBlank(), name + " is required");
        return value;
    }

    private static void assertEmpty(Path path) throws IOException {
        try (var files = Files.list(path)) { assertEquals(0L, files.count(), "Multipart temporary directory must remain empty"); }
    }
}

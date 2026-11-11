package kr.co.pillguide.backend.api.scan.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.netty.channel.ChannelOption;
import kr.co.pillguide.backend.api.drug.reference.SupportedDrugCatalog;
import kr.co.pillguide.backend.api.scan.exception.ScanException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

@Component
public final class AiClient {
    private static final int MAX_DETECTIONS = 300;
    private final AiProperties properties;
    private final SupportedDrugCatalog catalog;
    private final ObjectMapper mapper;
    private final WebClient client;

    private record Response(int status, String retryAfter, String body) { }

    public AiClient(AiProperties properties, SupportedDrugCatalog catalog, ObjectMapper mapper) {
        this.properties = properties;
        this.catalog = catalog;
        this.mapper = mapper;
        HttpClient http = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                properties.connectTimeoutMillis());
        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(properties.maxInMemoryBytes()));
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) builder.baseUrl(properties.baseUrl());
        client = builder.build();
    }

    public AiPredictionResult predict(PhotoInput input) {
        Objects.requireNonNull(input, "input");
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "사진 분석 서비스가 준비되지 않았습니다.", null);
        }
        ByteArrayResource bytes = new ByteArrayResource(input.bytes()) {
            @Override public String getFilename() { return "upload." + ("image/png".equals(input.mediaType()) ? "png" : "jpg"); }
        };
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", bytes).contentType(MediaType.parseMediaType(input.mediaType()));
        Response response;
        try {
            response = client.post().uri("/predict")
                    .header("X-API-Key", properties.apiKey())
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .exchangeToMono(http -> {
                        int status = http.statusCode().value();
                        String retryAfter = http.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                        if (status < 200 || status >= 300) {
                            return http.releaseBody().thenReturn(new Response(status, retryAfter, null));
                        }
                        return http.bodyToMono(String.class).defaultIfEmpty("")
                                .map(raw -> new Response(status, retryAfter, raw));
                    })
                    .timeout(Duration.ofMillis(properties.responseTimeoutMillis()))
                    .block();
        } catch (RuntimeException ex) {
            Throwable cause = Exceptions.unwrap(ex);
            if (cause instanceof TimeoutException) {
                throw error(HttpStatus.GATEWAY_TIMEOUT, "사진 분석 응답 시간이 초과됐습니다.", null);
            }
            if (cause instanceof WebClientRequestException) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "사진 분석 서비스에 연결할 수 없습니다.", null);
            }
            throw error(HttpStatus.BAD_GATEWAY, "사진 분석 응답을 처리할 수 없습니다.", null);
        }
        if (response == null) throw error(HttpStatus.BAD_GATEWAY, "사진 분석 응답이 비어 있습니다.", null);
        return switch (response.status()) {
            case 200 -> parse(response.body(), input);
            case 400, 422 -> throw error(HttpStatus.BAD_REQUEST, "사진 요청 형식이 올바르지 않습니다.", null);
            case 413 -> throw error(HttpStatus.PAYLOAD_TOO_LARGE, "사진 크기 제한을 초과했습니다.", null);
            case 415 -> throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않거나 손상된 사진입니다.", null);
            case 429, 503 -> throw error(HttpStatus.SERVICE_UNAVAILABLE, "사진 분석 서비스가 바쁩니다.",
                    boundedRetryAfter(response.retryAfter()));
            default -> throw error(HttpStatus.BAD_GATEWAY, "사진 분석 서비스 오류가 발생했습니다.", null);
        };
    }

    private AiPredictionResult parse(String raw, PhotoInput input) {
        try {
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(raw);
            if (root == null || !root.isObject() || !"H".equals(text(root, "model"))) throw invalid();
            String modelSha = text(root, "model_sha256");
            String mappingSha = text(root, "mapping_sha256");
            if (!sha(modelSha) || !sha(mappingSha)
                    || !modelSha.equals(properties.expectedModelSha256())
                    || !mappingSha.equals(properties.expectedMappingSha256())
                    || !mappingSha.equals(catalog.mappingSha256())) throw invalid();
            JsonNode image = root.path("image");
            int width = positiveInt(image.path("width"));
            int height = positiveInt(image.path("height"));
            if (width != input.width() || height != input.height() || (long) width * height > 20_000_000L)
                throw invalid();
            JsonNode rows = root.path("predictions");
            if (!rows.isArray() || rows.size() > MAX_DETECTIONS) throw invalid();
            List<AiPredictionResult.Prediction> predictions = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                JsonNode row = rows.get(i);
                if (!row.isObject() || positiveInt(row.path("detection_id")) != i + 1) throw invalid();
                JsonNode classNode = row.path("class_id");
                if (!classNode.isIntegralNumber() || !classNode.canConvertToInt()) throw invalid();
                int classId = classNode.intValue();
                String itemSeq = text(row, "item_seq");
                if (classId < 0 || classId >= 150 || !itemSeq.matches("[0-9]+")
                        || catalog.byClassId(classId).isEmpty()
                        || !catalog.byClassId(classId).orElseThrow().itemSeq().equals(itemSeq)) throw invalid();
                double confidence = unit(row.path("confidence"));
                JsonNode bbox = row.path("bbox_xyxy");
                if (!bbox.isArray() || bbox.size() != 4) throw invalid();
                List<Double> bounds = List.of(unit(bbox.get(0)), unit(bbox.get(1)),
                        unit(bbox.get(2)), unit(bbox.get(3)));
                if (bounds.get(0) >= bounds.get(2) || bounds.get(1) >= bounds.get(3)) throw invalid();
                JsonNode degraded = row.path("recognition_may_be_degraded");
                if (!degraded.isBoolean()) throw invalid();
                predictions.add(new AiPredictionResult.Prediction(i + 1, classId, itemSeq,
                        confidence, bounds, degraded.booleanValue()));
            }
            return new AiPredictionResult(width, height, modelSha, mappingSha, predictions);
        } catch (ScanException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode node = object.path(field);
        if (!node.isTextual() || node.textValue().isBlank()) throw invalid();
        return node.textValue();
    }

    private static int positiveInt(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) throw invalid();
        return node.intValue();
    }

    private static double unit(JsonNode node) {
        if (!node.isNumber()) throw invalid();
        double value = node.doubleValue();
        if (!Double.isFinite(value) || value < 0 || value > 1) throw invalid();
        return value;
    }

    private static boolean sha(String value) { return value.matches("[0-9a-f]{64}"); }

    private static Integer boundedRetryAfter(String raw) {
        if (raw == null) return null;
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 0 && value <= 5 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ScanException invalid() {
        return error(HttpStatus.BAD_GATEWAY, "사진 분석 응답이 올바르지 않습니다.", null);
    }

    private static ScanException error(HttpStatus status, String message, Integer retryAfterSeconds) {
        return new ScanException(status, message, retryAfterSeconds);
    }
}

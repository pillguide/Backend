package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Component
public final class MfdsClient {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 200;
    private static final int MAX_RETRIES = 2;

    public enum Service {
        EASY("DrbEasyDrugInfoService", "getDrbEasyDrugList", "itemSeq", "ServiceKey"),
        PERMIT_LIST("DrugPrdtPrmsnInfoService08", "getDrugPrdtPrmsnInq08", "prdlst_Stdr_code", "serviceKey"),
        PERMIT_DETAIL("DrugPrdtPrmsnInfoService08", "getDrugPrdtPrmsnDtlInq08", "item_seq", "serviceKey"),
        PERMIT_INGREDIENTS("DrugPrdtPrmsnInfoService08", "getDrugPrdtMcpnDtlInq08", "Item_seq", "serviceKey"),
        DUR_PRODUCT("DURPrdlstInfoService03", "getUsjntTabooInfoList03", "itemSeq", "serviceKey"),
        DUR_INGREDIENT("DURIrdntInfoService03", "getUsjntTabooInfoList02", null, "serviceKey");

        private final String host;
        private final String operation;
        private final String itemParam;
        private final String keyParam;

        Service(String host, String operation, String itemParam, String keyParam) {
            this.host = host;
            this.operation = operation;
            this.itemParam = itemParam;
            this.keyParam = keyParam;
        }
    }

    public record SourceData(Service service, String queryKey, LocalDateTime fetchedAt, List<JsonNode> records) {
        public SourceData {
            records = List.copyOf(records);
        }
    }

    private record PageResponse(int status, String retryAfter, String body) { }
    private record Page(int total, List<JsonNode> items) { }

    private final MfdsProperties properties;
    private final ObjectMapper mapper;
    private final WebClient client;
    private long nextRequestNanos;

    public MfdsClient(MfdsProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        HttpClient http = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                properties.connectTimeoutMillis());
        this.client = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(properties.maxInMemoryBytes()))
                .build();
    }

    public SourceData fetch(Service service, String itemSeq, Runnable beforeRequest) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(beforeRequest, "beforeRequest");
        if (service == Service.DUR_INGREDIENT ? itemSeq != null : itemSeq == null || !itemSeq.matches("[0-9]{1,32}")) {
            throw new MfdsFetchException("INVALID_QUERY_KEY", 0);
        }
        String key = keyFor(service);
        if (key == null || key.isBlank()) {
            throw new MfdsFetchException("MISSING_KEY", 0);
        }

        List<JsonNode> records = new ArrayList<>();
        Set<List<JsonNode>> seenPages = new HashSet<>();
        Integer expectedTotal = null;
        LocalDateTime fetchedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        for (int pageNumber = 1; pageNumber <= MAX_PAGES; pageNumber++) {
            Page page = fetchPage(service, itemSeq, key, pageNumber, beforeRequest);
            if (expectedTotal == null) expectedTotal = page.total();
            if (page.total() != expectedTotal) throw new MfdsFetchException("TOTAL_CHANGED", 0);
            if (!page.items().isEmpty() && !seenPages.add(page.items())) {
                throw new MfdsFetchException("REPEATED_PAGE", 0);
            }
            for (JsonNode row : page.items()) {
                if (service != Service.DUR_INGREDIENT) validateIdentity(service, itemSeq, row);
                records.add(row);
            }
            if (records.size() == expectedTotal) {
                return new SourceData(service, service == Service.DUR_INGREDIENT ? "ALL" : itemSeq,
                        fetchedAt, records);
            }
            if (records.size() > expectedTotal) throw new MfdsFetchException("COUNT_EXCEEDS_TOTAL", 0);
            if (page.items().isEmpty()) throw new MfdsFetchException("EARLY_EMPTY_PAGE", 0);
        }
        throw new MfdsFetchException("PAGE_LIMIT", 0);
    }

    private Page fetchPage(Service service, String itemSeq, String key, int pageNumber, Runnable beforeRequest) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            throttle();
            beforeRequest.run();
            PageResponse response;
            try {
                response = client.get()
                        .uri(uri -> {
                            var builder = uri.pathSegment(service.host, service.operation)
                                    .queryParam("pageNo", pageNumber)
                                    .queryParam("numOfRows", PAGE_SIZE)
                                    .queryParam("type", "json")
                                    .queryParam(service.keyParam, "{serviceKey}");
                            if (service.itemParam != null) builder.queryParam(service.itemParam, "{itemSeq}");
                            return service.itemParam == null
                                    ? builder.build(Map.of("serviceKey", key))
                                    : builder.build(Map.of("serviceKey", key, "itemSeq", itemSeq));
                        })
                        .accept(MediaType.APPLICATION_JSON)
                        .exchangeToMono(http -> {
                            int status = http.statusCode().value();
                            String retryAfter = http.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                            if (status < 200 || status >= 300) {
                                return http.releaseBody().thenReturn(new PageResponse(status, retryAfter, null));
                            }
                            return http.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new PageResponse(status, retryAfter, body));
                        })
                        .timeout(Duration.ofMillis(properties.responseTimeoutMillis()))
                        .block();
            } catch (RuntimeException ex) {
                Throwable cause = Exceptions.unwrap(ex);
                if (cause instanceof WebClientRequestException || cause instanceof TimeoutException) {
                    if (attempt < MAX_RETRIES) {
                        retryDelay(200L * (attempt + 1));
                        continue;
                    }
                    throw new MfdsFetchException(cause instanceof TimeoutException ? "HTTP_TIMEOUT" : "HTTP_TRANSPORT", 0);
                }
                throw new MfdsFetchException("HTTP_RESPONSE_ERROR", 0);
            }
            if (response == null) throw new MfdsFetchException("HTTP_EMPTY_RESPONSE", 0);
            if (response.status() == 429 || response.status() >= 500) {
                long delay = retryDelayMillis(response.retryAfter(), attempt);
                if (delay < 0) throw new MfdsFetchException("RATE_LIMIT_WAIT_REQUIRED", response.status());
                if (attempt < MAX_RETRIES) {
                    retryDelay(delay);
                    continue;
                }
                throw new MfdsFetchException("HTTP_RETRY_EXHAUSTED", response.status());
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new MfdsFetchException("HTTP_STATUS", response.status());
            }
            return parsePage(response.body());
        }
        throw new MfdsFetchException("HTTP_RETRY_EXHAUSTED", 0);
    }

    private Page parsePage(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            JsonNode response = root.has("response") ? root.path("response") : root;
            String resultCode = response.path("header").path("resultCode").asText();
            if (!Set.of("0", "00", "0000").contains(resultCode)) {
                throw new MfdsFetchException("API_RESULT", 0);
            }
            JsonNode body = response.path("body");
            JsonNode totalNode = body.path("totalCount");
            if (totalNode.isMissingNode() || totalNode.isNull()) throw new MfdsFetchException("MISSING_TOTAL", 0);
            int total = Integer.parseInt(totalNode.asText());
            if (total < 0) throw new IllegalArgumentException();
            List<JsonNode> items = normalizeItems(body.path("items"));
            if (total == 0 && !items.isEmpty()) throw new MfdsFetchException("COUNT_EXCEEDS_TOTAL", 0);
            return new Page(total, items);
        } catch (MfdsFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MfdsFetchException("INVALID_RESPONSE", 0);
        }
    }

    private static List<JsonNode> normalizeItems(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (node.isObject() && node.has("item")) node = node.get("item");
        if (node == null || node.isNull()) return List.of();
        List<JsonNode> items = new ArrayList<>();
        if (node.isArray()) node.forEach(items::add);
        else if (node.isObject()) items.add(node);
        else throw new IllegalArgumentException();
        List<JsonNode> normalized = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            if (item.isObject() && item.size() == 1 && item.has("item")) item = item.get("item");
            if (item == null || !item.isObject()) throw new IllegalArgumentException();
            normalized.add(item);
        }
        return normalized;
    }

    private static void validateIdentity(Service service, String itemSeq, JsonNode row) {
        String field = service == Service.EASY ? "itemSeq" : "ITEM_SEQ";
        if (!itemSeq.equals(row.path(field).asText())) throw new MfdsFetchException("ITEM_SEQ_MISMATCH", 0);
        if (service == Service.PERMIT_LIST && row.hasNonNull("PRDLST_STDR_CODE")
                && !itemSeq.equals(row.path("PRDLST_STDR_CODE").asText())) {
            throw new MfdsFetchException("ITEM_SEQ_MISMATCH", 0);
        }
    }

    private String keyFor(Service service) {
        return switch (service) {
            case EASY -> properties.easyKey();
            case PERMIT_LIST, PERMIT_DETAIL, PERMIT_INGREDIENTS -> properties.permitKey();
            case DUR_PRODUCT -> properties.durProductKey();
            case DUR_INGREDIENT -> properties.durIngredientKey();
        };
    }

    private synchronized void throttle() {
        long interval = Duration.ofMillis(properties.minIntervalMillis()).toNanos();
        long now = System.nanoTime();
        long wait = Math.max(0, nextRequestNanos - now);
        if (wait > 0) retryDelay(Duration.ofNanos(wait).toMillis() + 1);
        nextRequestNanos = System.nanoTime() + interval;
    }

    private long retryDelayMillis(String retryAfter, int attempt) {
        long fallback = 200L * (attempt + 1);
        if (retryAfter == null) return fallback;
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds < 0) return fallback;
            long millis = Duration.ofSeconds(seconds).toMillis();
            return millis > properties.maxRetryAfterMillis() ? -1 : millis;
        } catch (NumberFormatException | ArithmeticException ignored) {
            try {
                long millis = Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis();
                millis = Math.max(0, millis);
                return millis > properties.maxRetryAfterMillis() ? -1 : millis;
            } catch (DateTimeParseException | ArithmeticException ignoredDate) {
                return fallback;
            }
        }
    }

    private static void retryDelay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MfdsFetchException("INTERRUPTED", 0);
        }
    }
}

package kr.co.pillguide.backend.api.drug.reference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration values are initial bounds, to be tuned from measured MFDS limits. */
@Component
public record MfdsProperties(
        String baseUrl,
        String easyKey,
        String permitKey,
        String durProductKey,
        String durIngredientKey,
        int connectTimeoutMillis,
        long responseTimeoutMillis,
        int maxInMemoryBytes,
        long minIntervalMillis,
        long maxRetryAfterMillis
) {
    public MfdsProperties(
            @Value("${reference.mfds.base-url:https://apis.data.go.kr/1471000}") String baseUrl,
            @Value("${reference.mfds.easy-key:${drug.easy.service-key:}}") String easyKey,
            @Value("${reference.mfds.permit-key:${drug.prmsn.service-key:}}") String permitKey,
            @Value("${reference.mfds.dur-product-key:}") String durProductKey,
            @Value("${reference.mfds.dur-ingredient-key:}") String durIngredientKey,
            @Value("${reference.mfds.connect-timeout-millis:10000}") int connectTimeoutMillis,
            @Value("${reference.mfds.response-timeout-millis:45000}") long responseTimeoutMillis,
            @Value("${reference.mfds.max-in-memory-bytes:8388608}") int maxInMemoryBytes,
            @Value("${reference.mfds.min-interval-millis:400}") long minIntervalMillis,
            @Value("${reference.mfds.max-retry-after-millis:2000}") long maxRetryAfterMillis
    ) {
        if (baseUrl == null || baseUrl.isBlank() || connectTimeoutMillis <= 0 || responseTimeoutMillis <= 0
                || maxInMemoryBytes <= 0 || minIntervalMillis < 0 || maxRetryAfterMillis < 0) {
            throw new IllegalArgumentException("Invalid MFDS client configuration");
        }
        this.baseUrl = baseUrl;
        this.easyKey = easyKey;
        this.permitKey = permitKey;
        this.durProductKey = durProductKey;
        this.durIngredientKey = durIngredientKey;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.responseTimeoutMillis = responseTimeoutMillis;
        this.maxInMemoryBytes = maxInMemoryBytes;
        this.minIntervalMillis = minIntervalMillis;
        this.maxRetryAfterMillis = maxRetryAfterMillis;
    }
}

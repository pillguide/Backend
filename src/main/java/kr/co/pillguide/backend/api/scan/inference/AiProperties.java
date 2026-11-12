package kr.co.pillguide.backend.api.scan.inference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AiProperties(String baseUrl, String apiKey, String expectedModelSha256,
                           String expectedMappingSha256, int connectTimeoutMillis,
                           long responseTimeoutMillis, int maxInMemoryBytes) {
    public AiProperties(
            @Value("${scan.ai.base-url:}") String baseUrl,
            @Value("${scan.ai.api-key:}") String apiKey,
            @Value("${scan.ai.expected-model-sha256:fb11caa45b8f80e077aaa12072915dccca27e704b7f3ecffa266370a3fb500b7}") String expectedModelSha256,
            @Value("${scan.ai.expected-mapping-sha256:71569e7f4d277b5c6e69930086db7d52920a1c12dae3325a9cbc2234829e8bce}") String expectedMappingSha256,
            @Value("${scan.ai.connect-timeout-millis:10000}") int connectTimeoutMillis,
            @Value("${scan.ai.response-timeout-millis:60000}") long responseTimeoutMillis,
            @Value("${scan.ai.max-in-memory-bytes:1048576}") int maxInMemoryBytes
    ) {
        if (connectTimeoutMillis <= 0 || responseTimeoutMillis <= 0 || maxInMemoryBytes <= 0) {
            throw new IllegalArgumentException("Invalid AI client bounds");
        }
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.expectedModelSha256 = expectedModelSha256;
        this.expectedMappingSha256 = expectedMappingSha256;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.responseTimeoutMillis = responseTimeoutMillis;
        this.maxInMemoryBytes = maxInMemoryBytes;
    }
}

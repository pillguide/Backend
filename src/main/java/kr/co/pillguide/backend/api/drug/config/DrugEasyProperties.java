package kr.co.pillguide.backend.api.drug.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "drug.easy")
public record DrugEasyProperties(
        String serviceKey
) {
}

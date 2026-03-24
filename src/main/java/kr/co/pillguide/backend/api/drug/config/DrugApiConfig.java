package kr.co.pillguide.backend.api.drug.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DrugApiProperties.class)
public class DrugApiConfig {
}

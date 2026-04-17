package kr.co.pillguide.backend.api.drug.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value = {
        DrugEasyProperties.class
})
public class DrugApiConfig {
}

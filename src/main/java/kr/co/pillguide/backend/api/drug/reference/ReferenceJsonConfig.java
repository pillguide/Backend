package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReferenceJsonConfig {
    // MFDS raw-tree parsing uses the existing Jackson 2 dependency, independently of Boot 4's MVC mapper.
    @Bean
    ObjectMapper referenceObjectMapper() {
        return new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

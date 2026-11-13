package kr.co.pillguide.backend.api.drug.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class RepresentativeImageConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "representative.images.enabled", havingValue = "true")
    S3Presigner representativeImagePresigner(@Value("${AWS_REGION:ap-northeast-2}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}

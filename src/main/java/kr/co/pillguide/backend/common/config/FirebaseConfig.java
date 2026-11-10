package kr.co.pillguide.backend.common.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    public ServiceAccountCredentials fcmCredentials(
            ResourceLoader resourceLoader,
            @Value("${fcm.certification:}") String certification) {

        if (certification.isBlank()) {
            throw new IllegalStateException(
                    "FCM이 활성화되었지만 인증 파일 경로가 없습니다."
            );
        }

        try (InputStream stream =
                     resourceLoader.getResource(certification).getInputStream()) {

            return ServiceAccountCredentials.fromStream(stream);

        } catch (Exception exception) {
            // 인증 키 내용이 예외 로그에 노출되지 않도록 처리
            throw new IllegalStateException(
                    "Firebase 초기화 실패: 인증 파일 경로와 형식을 확인하세요."
            );
        }
    }

    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(ServiceAccountCredentials credentials) {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(credentials.getProjectId())
                .setConnectTimeout(2000).setReadTimeout(5000).setWriteTimeout(5000)
                .build();
        return FirebaseApp.initializeApp(options, "pillguide-fcm");
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}

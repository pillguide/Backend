package kr.co.pillguide.backend.common.config;

import com.google.firebase.FirebaseApp;
import com.google.gson.Gson;
import kr.co.pillguide.backend.api.member.service.FcmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest {
    @TempDir Path temporary;
    final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfig.class, FcmService.class);

    @Test
    void disabledFcmNeedsNoCredentialFile() {
        context.withPropertyValues("fcm.enabled=false").run(application -> {
            assertThat(application).hasNotFailed().doesNotHaveBean(FcmService.class)
                    .doesNotHaveBean(FirebaseApp.class);
        });
    }

    @Test
    void enabledFcmConstructsRealBeansWithoutNetworkAccess() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path file = temporary.resolve("synthetic-service-account.json");
        Files.writeString(file, new Gson().toJson(Map.of(
                "type", "service_account", "project_id", "test-only", "private_key_id", "test-only",
                "private_key", privateKey, "client_email", "test@test-only.iam.gserviceaccount.com",
                "client_id", "123", "token_uri", "http://127.0.0.1:1/never-contacted")));
        context.withPropertyValues("fcm.enabled=true", "fcm.certification=" + file.toUri())
                .run(application -> assertThat(application).hasNotFailed()
                        .hasSingleBean(FcmService.class).hasSingleBean(FirebaseApp.class));
    }

    @Test
    void enabledFcmFailsClearlyWhenCredentialsAreMissing() {
        context.withPropertyValues("fcm.enabled=true", "fcm.certification=")
                .run(application -> assertThat(application).hasFailed());
    }
}

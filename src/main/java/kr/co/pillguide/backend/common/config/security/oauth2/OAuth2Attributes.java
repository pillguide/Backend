package kr.co.pillguide.backend.common.config.security.oauth2;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

public final class OAuth2Attributes {

    private OAuth2Attributes() {}

    public record UserProfile(
        String provider,
        String providerId,
        String email,
        String name,
        Map<String, Object> attributes,
        String nameAttributeKey
    ) {}

    @SuppressWarnings("unchecked")
    public static UserProfile extract(OAuth2UserRequest req, OAuth2User user) {

        // google/naver/kakao
        String registrationId = req.getClientRegistration().getRegistrationId();
        Map<String, Object> a = user.getAttributes();

        return switch (registrationId) {
            case "google" -> new UserProfile(
                    "google",
                    (String) a.get("sub"),
                    (String) a.get("email"),
                    (String) a.getOrDefault("name",""), a, "sub");
            case "naver" -> {
                    Map<String, Object> resp = (Map<String, Object>) a.get("response");
                    yield new UserProfile(
                            "naver",
                            (String) resp.get("id"),
                            (String) resp.get("email"),
                            (String) resp.getOrDefault("name",""), a, "response");
            }
            case "kakao" -> {
                String id = String.valueOf(a.get("id"));
                Map<String, Object> profile = (Map<String, Object>) a.get("kakao_account");

            }
        }
    }
}

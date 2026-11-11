package kr.co.pillguide.backend.common.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeChallengeAuthorizationRequestTest {
    private final ExchangeChallengeAuthorizationRequestResolver resolver =
            new ExchangeChallengeAuthorizationRequestResolver(new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("google")
                            .clientId("test-client")
                            .clientSecret("test-secret")
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                            .authorizationUri("https://provider.example/authorize")
                            .tokenUri("https://provider.example/token")
                            .userInfoUri("https://provider.example/userinfo")
                            .userNameAttributeName("sub")
                            .clientName("google")
                            .build()));

    @Test
    void storesChallengeServerSideAndPreservesProviderState() {
        MockHttpServletRequest start = new MockHttpServletRequest("GET", "/api/v1/oauth2/authorize/google");
        String challenge = "A".repeat(43);
        start.setParameter("exchange_challenge", challenge);
        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(start);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getState()).isNotBlank();
        String storedChallenge = authorizationRequest.getAttribute(
                ExchangeChallengeAuthorizationRequestResolver.CHALLENGE_ATTRIBUTE);
        assertThat(storedChallenge).isEqualTo(challenge);
        assertThat(authorizationRequest.getAdditionalParameters()).doesNotContainKey("exchange_challenge");

        ExchangeChallengeAuthorizationRequestRepository repository =
                new ExchangeChallengeAuthorizationRequestRepository();
        repository.saveAuthorizationRequest(authorizationRequest, start, new MockHttpServletResponse());
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        callback.setSession(start.getSession());
        callback.setParameter("state", authorizationRequest.getState());
        assertThat(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse()))
                .isNotNull();
        assertThat(callback.getAttribute(ExchangeChallengeAuthorizationRequestResolver.CHALLENGE_ATTRIBUTE))
                .isEqualTo(challenge);
    }

    @Test
    void invalidChallengeAndWrongProviderStateAreRejected() {
        MockHttpServletRequest start = new MockHttpServletRequest("GET", "/api/v1/oauth2/authorize/google");
        start.setParameter("exchange_challenge", "short");
        assertThatThrownBy(() -> resolver.resolve(start)).isInstanceOf(OAuth2AuthenticationException.class);

        start.setParameter("exchange_challenge", "A".repeat(43));
        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(start);
        ExchangeChallengeAuthorizationRequestRepository repository =
                new ExchangeChallengeAuthorizationRequestRepository();
        repository.saveAuthorizationRequest(authorizationRequest, start, new MockHttpServletResponse());
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        callback.setSession(start.getSession());
        callback.setParameter("state", "wrong-state");
        assertThat(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse())).isNull();
        assertThat(callback.getAttribute(ExchangeChallengeAuthorizationRequestResolver.CHALLENGE_ATTRIBUTE)).isNull();
    }
}

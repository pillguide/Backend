package kr.co.pillguide.backend.common.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ExchangeChallengeAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    public static final String CHALLENGE_ATTRIBUTE = "pillguide.exchange.challenge";
    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public ExchangeChallengeAuthorizationRequestResolver(ClientRegistrationRepository clients) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, "/api/v1/oauth2/authorize");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withChallenge(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withChallenge(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest withChallenge(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        String challenge = request.getParameter("exchange_challenge");
        if (challenge == null || !CHALLENGE.matcher(challenge).matches()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_exchange_challenge"));
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .attributes(attributes -> attributes.put(CHALLENGE_ATTRIBUTE, challenge))
                .build();
    }
}

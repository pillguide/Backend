package kr.co.pillguide.backend.common.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler
        implements AuthenticationFailureHandler {

    @Value("${app.oauth2.frontend-failure-url:http://localhost:3000/login}")
    private String frontendFailureUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String errorCode = "oauth_failed";
        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String providerCode = oauthEx.getError().getErrorCode();
            if ("access_denied".equals(providerCode) || "invalid_exchange_challenge".equals(providerCode)) {
                errorCode = providerCode;
            }
        }
        log.warn("OAuth2 login failed - code={}", errorCode);

        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendFailureUrl)
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUriString();

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect(redirectUrl);
    }
}

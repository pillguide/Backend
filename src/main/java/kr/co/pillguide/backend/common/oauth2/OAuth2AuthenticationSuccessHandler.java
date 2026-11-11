package kr.co.pillguide.backend.common.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.pillguide.backend.api.member.service.LoginExchangeService;
import kr.co.pillguide.backend.common.security.SecurityMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final LoginExchangeService loginExchangeService;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    @Value("${app.oauth2.frontend-success-url:http://localhost:3000/oauth/callback}")
    private String frontendSuccessUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        Object attribute = request.getAttribute(ExchangeChallengeAuthorizationRequestResolver.CHALLENGE_ATTRIBUTE);
        if (!(attribute instanceof String challenge) || !LoginExchangeService.isChallenge(challenge)) {
            failureHandler.onAuthenticationFailure(request, response,
                    new OAuth2AuthenticationException(new OAuth2Error("invalid_exchange_challenge")));
            return;
        }

        SecurityMember principal = (SecurityMember) authentication.getPrincipal();
        Long memberId = principal.getMember().getId();
        String code = loginExchangeService.issueCode(memberId, challenge);
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendSuccessUrl)
                .queryParam("code", code)
                .build()
                .toUriString();
        log.info("OAuth2 login exchange issued - memberId={}", memberId);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect(redirectUrl);
    }
}

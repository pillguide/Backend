package kr.co.pillguide.backend.common.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final JwtService jwtService;

    private static final String FRONT_CALLBACK_URL =
            "http://localhost:3000/oauth/callback";

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        SecurityMember principal =
                (SecurityMember) authentication.getPrincipal();

        Long memberId = principal.getMemberId();
        boolean profileCompleted = principal.getMember().isProfileCompleted();

        String accessToken = jwtService.createAccessToken(memberId);
        String refreshToken = jwtService.createRefreshToken(memberId);

        // 프론트 리다이렉트 URL 구성
        String redirectUrl = UriComponentsBuilder
                .fromUriString(FRONT_CALLBACK_URL)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("profileCompleted", profileCompleted)
                .build()
                .toUriString();

        log.info(
                "OAuth2 login success - memberId={}, profileCompleted={}",
                memberId, profileCompleted
        );

        // 프론트로 리다이렉트
        response.sendRedirect(redirectUrl);
    }
}

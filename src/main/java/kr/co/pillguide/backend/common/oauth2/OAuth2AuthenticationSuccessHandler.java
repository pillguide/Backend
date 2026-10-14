package kr.co.pillguide.backend.common.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import kr.co.pillguide.backend.api.member.repository.RefreshTokenRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final String FRONT_CALLBACK_URL =
            "http://localhost:3000/oauth/callback";

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException{

        SecurityMember principal =
                (SecurityMember) authentication.getPrincipal();

        Member member = principal.getMember();
        Long memberId = member.getId();

        // 1️⃣ 기존 Refresh Token 제거 (중요)
        refreshTokenRepository.deleteByMemberId(memberId);

        // 2️⃣ 새 토큰 생성
        String accessToken = jwtService.createAccessToken(memberId);
        String refreshTokenValue = jwtService.createRefreshToken(memberId);

        // 3️⃣ Refresh Token DB 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .member(member)
                .expiresAt(LocalDateTime.now().plusDays(60))
                .build();

        refreshTokenRepository.save(refreshToken);

        boolean profileCompleted = member.isProfileCompleted();

        String redirectUrl = UriComponentsBuilder
                .fromUriString(FRONT_CALLBACK_URL)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshTokenValue)
                .queryParam("profileCompleted", profileCompleted)
                .build()
                .toUriString();

        log.info(
                "OAuth2 login success - memberId={}, profileCompleted={}",
                memberId, profileCompleted
        );

        response.sendRedirect(redirectUrl);
    }
}

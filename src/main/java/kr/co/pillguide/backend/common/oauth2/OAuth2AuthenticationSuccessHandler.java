package kr.co.pillguide.backend.common.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        SecurityMember principal =
                (SecurityMember) authentication.getPrincipal();

        Long memberId = principal.getMemberId();

        String accessToken = jwtService.createAccessToken(memberId);
        String refreshToken = jwtService.createRefreshToken(memberId);

//프론트 리다이렉트 아직
//        response.sendRedirect(
//                "http://localhost:3000/oauth/callback?accessToken=" + accessToken
//        );
        //일단 백엔드만 테스트
        response.sendRedirect("http://localhost:8080/swagger-ui.html");
    }
}

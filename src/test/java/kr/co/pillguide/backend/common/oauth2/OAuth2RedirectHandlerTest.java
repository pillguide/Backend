package kr.co.pillguide.backend.common.oauth2;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import kr.co.pillguide.backend.api.member.repository.RefreshTokenRepository;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OAuth2RedirectHandlerTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://pillguide.site", "https://dev.pillguide.site", "http://localhost:3000"})
    void successReturnsToConfiguredFrontendAndPreservesTokenFlow(String origin) throws Exception {
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        Member member = mock(Member.class);
        Authentication authentication = mock(Authentication.class);
        when(member.getId()).thenReturn(42L);
        when(member.isProfileCompleted()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new SecurityMember(member));
        when(jwtService.createAccessToken(42L)).thenReturn("test-access");
        when(jwtService.createRefreshToken(42L)).thenReturn("test-refresh");
        var handler = new OAuth2AuthenticationSuccessHandler(jwtService, repository);
        ReflectionTestUtils.setField(handler, "frontendSuccessUrl", origin + "/oauth/callback");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(origin
                + "/oauth/callback?accessToken=test-access&refreshToken=test-refresh&profileCompleted=true");
        verify(repository).deleteByMemberId(42L);
        var savedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(savedToken.capture());
        assertThat(savedToken.getValue().getToken()).isEqualTo("test-refresh");
        assertThat(savedToken.getValue().getMember()).isSameAs(member);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://pillguide.site", "https://dev.pillguide.site", "http://localhost:3000"})
    void failureReturnsToConfiguredLoginAndPreservesErrorCode(String origin) throws Exception {
        var handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "frontendFailureUrl", origin + "/login");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));
        assertThat(response.getRedirectedUrl()).isEqualTo(origin + "/login?error=access_denied");

        response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("test failure"));
        assertThat(response.getRedirectedUrl()).isEqualTo(origin + "/login?error=oauth_failed");
    }
}

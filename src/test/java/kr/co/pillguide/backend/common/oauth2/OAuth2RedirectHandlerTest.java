package kr.co.pillguide.backend.common.oauth2;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.service.LoginExchangeService;
import kr.co.pillguide.backend.common.security.SecurityMember;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Test
    void missingExchangeChallengeCannotIssueCode() throws Exception {
        LoginExchangeService exchanges = mock(LoginExchangeService.class);
        OAuth2AuthenticationFailureHandler failureHandler = mock(OAuth2AuthenticationFailureHandler.class);
        var handler = new OAuth2AuthenticationSuccessHandler(exchanges, failureHandler);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, mock(Authentication.class));

        verifyNoInteractions(exchanges);
        verify(failureHandler).onAuthenticationFailure(eq(request), eq(response),
                any(OAuth2AuthenticationException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://pillguide.site", "https://dev.pillguide.site", "http://localhost:3000"})
    void successRedirectContainsOnlyOneTimeCode(String origin) throws Exception {
        LoginExchangeService exchanges = mock(LoginExchangeService.class);
        OAuth2AuthenticationFailureHandler failureHandler = mock(OAuth2AuthenticationFailureHandler.class);
        Member member = mock(Member.class);
        Authentication authentication = mock(Authentication.class);
        when(member.getId()).thenReturn(42L);
        when(authentication.getPrincipal()).thenReturn(new SecurityMember(member));
        String challenge = "A".repeat(43);
        when(exchanges.issueCode(42L, challenge)).thenReturn("opaque-code");
        var handler = new OAuth2AuthenticationSuccessHandler(exchanges, failureHandler);
        ReflectionTestUtils.setField(handler, "frontendSuccessUrl", origin + "/oauth/callback");
        var request = new MockHttpServletRequest();
        request.setAttribute(ExchangeChallengeAuthorizationRequestResolver.CHALLENGE_ATTRIBUTE, challenge);
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(origin + "/oauth/callback?code=opaque-code");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        verify(exchanges).issueCode(42L, challenge);
        verifyNoInteractions(failureHandler);
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
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");

        response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("test failure"));
        assertThat(response.getRedirectedUrl()).isEqualTo(origin + "/login?error=oauth_failed");

        response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("unsafe-code?token=secret", "secret description", null)));
        assertThat(response.getRedirectedUrl()).isEqualTo(origin + "/login?error=oauth_failed");
    }
}

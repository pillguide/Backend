package kr.co.pillguide.backend.common.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationProcessingFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final MemberRepository members;
    private final FilterExceptionHandler errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        return path.equals("/api/v1/auth/reissue") || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/exchange")
                || path.startsWith("/api/v1/oauth2/authorize/") || path.startsWith("/login/oauth2/code/")
                || path.equals("/actuator/health") || path.equals("/actuator/health/readiness")
                || path.equals("/actuator/health/liveness") || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html") || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/") || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = jwtService.extractAccessToken(request).orElse(null);
        if (token != null) {
            try {
                var claims = jwtService.parseAccess(token);
                var member = members.findById(claims.memberId()).orElseThrow(() ->
                        new UnauthorizedException("인증되지 않은 사용자입니다."));
                if (member.getAuthVersion() != claims.authVersion()) {
                    throw new UnauthorizedException("폐기된 인증입니다.");
                }
                var principal = new SecurityMember(member);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (UnauthorizedException e) {
                SecurityContextHolder.clearContext();
                errors.commence(request, response, new BadCredentialsException("Invalid authentication"));
                return;
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                errors.internalError(response, e);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

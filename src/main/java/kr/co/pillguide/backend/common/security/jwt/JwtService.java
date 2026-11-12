package kr.co.pillguide.backend.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    @Value("${jwt.secretKey}") private String secretKey;
    @Value("${jwt.access.expiration}") private long accessTokenExpiration;
    @Value("${jwt.refresh.expiration}") private long refreshTokenExpiration;
    @Value("${jwt.access.header}") private String accessHeader;

    public record TokenClaims(Long memberId, long authVersion, Instant expiresAt) { }

    public Optional<String> extractAccessToken(HttpServletRequest request) {
        String header = request.getHeader(accessHeader);
        return header != null && header.startsWith("Bearer ")
                ? Optional.of(header.substring(7)) : Optional.empty();
    }

    public String createAccessToken(Long memberId, long authVersion) {
        return create(memberId, authVersion, "access", accessTokenExpiration);
    }

    public String createRefreshToken(Long memberId, long authVersion) {
        return create(memberId, authVersion, "refresh", refreshTokenExpiration);
    }

    public TokenClaims parseAccess(String token) { return parse(token, "access"); }
    public TokenClaims parseRefresh(String token) { return parse(token, "refresh"); }

    private String create(Long memberId, long authVersion, String purpose, long expiration) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(memberId.toString())
                .setId(UUID.randomUUID().toString()).claim("purpose", purpose)
                .claim("authVersion", authVersion)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusMillis(expiration)))
                .signWith(signingKey(), SignatureAlgorithm.HS256).compact();
    }

    private TokenClaims parse(String token, String purpose) {
        if (token == null || token.isBlank()) throw invalidToken();
        Key key = signingKey();
        try {
            var jwt = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            Claims claims = jwt.getBody();
            if (!SignatureAlgorithm.HS256.getValue().equals(jwt.getHeader().getAlgorithm())
                    || !purpose.equals(claims.get("purpose", String.class))
                    || claims.getId() == null || claims.getId().isBlank()
                    || claims.getExpiration() == null || claims.getIssuedAt() == null) {
                throw invalidToken();
            }
            Object version = claims.get("authVersion");
            if (!(version instanceof Integer || version instanceof Long)) throw invalidToken();
            long authVersion = ((Number) version).longValue();
            Long memberId = Long.valueOf(claims.getSubject());
            if (memberId <= 0 || authVersion < 0) throw invalidToken();
            return new TokenClaims(memberId, authVersion, claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            // Parser failures can contain claims: do not log their message or stack.
            throw invalidToken();
        }
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException("유효하지 않은 인증 토큰입니다.");
    }
}

package kr.co.pillguide.backend.api.member.service;

import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import kr.co.pillguide.backend.api.member.repository.RefreshTokenRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EntityManager em;

    public TokenResponseDTO loginSuccess(Member principal) {
        Member member = lockMember(principal.getId());
        return issue(member, refreshTokenRepository.findByMemberId(member.getId()).orElse(null));
    }

    public TokenResponseDTO reissue(String value) {
        var claims = jwtService.parseRefresh(value);
        Member member = lockMember(claims.memberId());
        return issue(member, currentRefresh(member, claims, value));
    }

    public void logout(String value) {
        var claims = jwtService.parseRefresh(value);
        Member member = lockMember(claims.memberId());
        RefreshToken token = currentRefresh(member, claims, value);
        member.revokeAuthentication();
        // Keep the inverse cascade collection consistent with the row deletion.
        member.removeRefreshToken(token);
        refreshTokenRepository.delete(token);
    }

    private RefreshToken currentRefresh(Member member, JwtService.TokenClaims claims, String value) {
        if (member.getAuthVersion() != claims.authVersion()) throw invalidRefresh();
        RefreshToken token = refreshTokenRepository.findByMemberId(member.getId())
                .orElseThrow(this::invalidRefresh);
        if (!token.getToken().equals(value) || token.isExpired()) throw invalidRefresh();
        return token;
    }

    private TokenResponseDTO issue(Member member, RefreshToken existing) {
        String access = jwtService.createAccessToken(member.getId(), member.getAuthVersion());
        String refresh = jwtService.createRefreshToken(member.getId(), member.getAuthVersion());
        LocalDateTime expires = LocalDateTime.ofInstant(jwtService.parseRefresh(refresh).expiresAt(), ZoneId.systemDefault());
        if (existing == null) {
            refreshTokenRepository.save(RefreshToken.builder().member(member).token(refresh).expiresAt(expires).build());
        } else {
            existing.updateToken(refresh, expires);
        }
        return new TokenResponseDTO(access, refresh);
    }

    private Member lockMember(Long memberId) {
        try {
            return MemberLocks.lock(em, memberId);
        } catch (NotFoundException e) {
            throw invalidRefresh();
        }
    }

    private UnauthorizedException invalidRefresh() {
        return new UnauthorizedException("유효하지 않은 Refresh Token입니다.");
    }
}

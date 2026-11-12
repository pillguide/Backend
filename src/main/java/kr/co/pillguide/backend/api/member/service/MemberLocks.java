package kr.co.pillguide.backend.api.member.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import java.time.Instant;

/** Shared lock order starts at the member, before reminder or token changes. */
public final class MemberLocks {
    private MemberLocks() { }

    public static Member lockAuthenticated(EntityManager em, JwtService.TokenClaims claims) {
        if (claims == null || claims.memberId() == null || claims.expiresAt() == null
                || !Instant.now().isBefore(claims.expiresAt())) {
            throw new UnauthorizedException("인증이 만료되었습니다.");
        }
        Member member;
        try { member = lock(em, claims.memberId()); }
        catch (NotFoundException ex) { throw new UnauthorizedException("인증되지 않은 사용자입니다."); }
        if (member.getAuthVersion() != claims.authVersion() || !Instant.now().isBefore(claims.expiresAt())) {
            throw new UnauthorizedException("폐기되었거나 만료된 인증입니다.");
        }
        return member;
    }

    public static Member lock(EntityManager em, Long id) {
        if (id == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        Member member = em.find(Member.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (member == null) {
            throw new NotFoundException("회원을 찾을 수 없습니다.");
        }
        // Authentication or earlier reads may have already loaded a stale managed instance.
        em.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        return member;
    }
}

package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.repository.RefreshTokenRepository;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    //나중에 혹시 일반 로그인 추가하게 되면 사용
    public TokenResponseDTO loginSuccess(Member member) {

        Long memberId = member.getId();

        refreshTokenRepository.deleteByMemberId(memberId);

        // 토큰 생성
        String accessToken = jwtService.createAccessToken(memberId);
        String refreshTokenValue = jwtService.createRefreshToken(memberId);

        // refresh token 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .member(member)
                .expiresAt(LocalDateTime.now().plusDays(60))
                .build();

        refreshTokenRepository.save(refreshToken);

        return new TokenResponseDTO(accessToken, refreshTokenValue);
    }

    /**
     * Refresh Token 재발급
     */
    public TokenResponseDTO reissue(String refreshTokenValue) {

        // 1️⃣ JWT 자체 검증
        if (!jwtService.isTokenValid(refreshTokenValue)) {
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다.");
        }

        // 2️⃣ DB 조회
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenValue)
                .orElseThrow(() ->
                        new UnauthorizedException("존재하지 않는 Refresh Token입니다.")
                );

        // 3️⃣ 만료 검증 (DB 기준)
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("만료된 Refresh Token입니다.");
        }

        Member member = refreshToken.getMember();
        Long memberId = member.getId();

        // 4️⃣ 새 토큰 발급
        String newAccessToken = jwtService.createAccessToken(memberId);
        String newRefreshTokenValue = jwtService.createRefreshToken(memberId);

        // 5️⃣ Refresh Token rotation
        refreshToken.updateToken(
                newRefreshTokenValue,
                LocalDateTime.now().plusDays(60)
        );

        return new TokenResponseDTO(
                newAccessToken,
                newRefreshTokenValue
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }
}
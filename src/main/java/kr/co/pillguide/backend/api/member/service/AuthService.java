package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.repository.RefreshTokenRepository;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    public TokenResponseDTO reissue(String refreshTokenValue) {

        // 1️⃣ JWT 자체 검증
        if (!jwtService.isTokenValid(refreshTokenValue)) {
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다.");
        }

        // 2️⃣ 토큰에서 memberId 추출
        Long memberId = jwtService.extractMemberId(refreshTokenValue);

        // 3️⃣ DB에 저장된 Refresh Token 조회
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() ->
                        new UnauthorizedException("존재하지 않는 Refresh Token입니다.")
                );

        // 4️⃣ 만료 여부(DB 기준)
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("만료된 Refresh Token입니다.");
        }

        // 5️⃣ 사용자 일치 검증
        if (!refreshToken.getMember().getId().equals(memberId)) {
            throw new UnauthorizedException("Refresh Token 정보가 일치하지 않습니다.");
        }

        Member member = refreshToken.getMember();

        // 6️⃣ 새 토큰 생성
        String newAccessToken = jwtService.createAccessToken(memberId);
        String newRefreshToken = jwtService.createRefreshToken(memberId);

        // 7️⃣ Refresh Token 로테이션
        refreshToken.updateToken(
                newRefreshToken,
                LocalDateTime.now().plusDays(60)
        );

        return new TokenResponseDTO(newAccessToken, newRefreshToken);
    }
}


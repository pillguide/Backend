package kr.co.pillguide.backend.api.member.repository;

import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByMemberId(Long memberId);

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByMemberId(Long memberId);
}

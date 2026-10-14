package kr.co.pillguide.backend.api.member.repository;

import kr.co.pillguide.backend.api.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByMemberId(Long memberId);

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    @Transactional
    @Modifying
    void deleteByMemberId(Long memberId);
}

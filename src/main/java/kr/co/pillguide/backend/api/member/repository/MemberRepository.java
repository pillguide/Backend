package kr.co.pillguide.backend.api.member.repository;

import kr.co.pillguide.backend.api.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>{

    Optional<Member> findByEmailAndProvider(String email, String provider);

    Optional<Member> findByOauthIdAndProvider(String oauthId, String provider);

    Optional<Member> findByEmail(String email);
}

package kr.co.pillguide.backend.api.member.repository;

import kr.co.pillguide.backend.api.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>{

    Optional<Member> findByOauthIdAndProvider(String oauthId, String provider);

    Optional<Member> findByEmail(String email);

    @Query("select m.id from Member m where m.fcmToken = :token")
    Optional<Long> findIdByFcmToken(@Param("token") String token);

}

package kr.co.pillguide.backend.api.member.repository;

import kr.co.pillguide.backend.api.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>{

    Optional<Member> findByOauthIdAndProvider(String oauthId, String provider);

    Optional<Member> findByEmail(String email);

    @Query("select m.id from Member m where m.fcmToken = :token")
    Optional<Long> findIdByFcmToken(@Param("token") String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Member m
        set m.fcmToken = null
        where m.fcmToken = :token
          and m.id <> :memberId
        """)
    int clearTokenFromOtherMembers(
            @Param("token") String token,
            @Param("memberId") Long memberId
    );

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Member m
        set m.fcmToken = null
        where m.id = :memberId
          and m.fcmToken = :token
        """)
    int clearFcmTokenIfMatches(
            @Param("memberId") Long memberId,
            @Param("token") String token
    );
}

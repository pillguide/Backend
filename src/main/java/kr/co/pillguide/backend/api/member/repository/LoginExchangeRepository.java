package kr.co.pillguide.backend.api.member.repository;

import jakarta.persistence.LockModeType;
import kr.co.pillguide.backend.api.member.entity.LoginExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginExchangeRepository extends JpaRepository<LoginExchange, Long> {
    @Query("select e.member.id from LoginExchange e where e.codeHash = :hash")
    Optional<Long> findMemberIdByCodeHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LoginExchange e where e.codeHash = :hash")
    Optional<LoginExchange> findForUpdateByCodeHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from LoginExchange e where e.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") LocalDateTime now);
}

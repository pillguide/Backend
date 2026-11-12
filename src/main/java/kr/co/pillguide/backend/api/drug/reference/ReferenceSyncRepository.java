package kr.co.pillguide.backend.api.drug.reference;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface ReferenceSyncRepository extends JpaRepository<ReferenceSyncState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReferenceSyncState s where s.id = 1")
    Optional<ReferenceSyncState> lockState();
}

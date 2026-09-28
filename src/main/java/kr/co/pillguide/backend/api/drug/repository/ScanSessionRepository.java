package kr.co.pillguide.backend.api.drug.repository;

import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanSessionRepository extends JpaRepository<ScanSession, Long> {

    @EntityGraph(attributePaths = "drugs")
    List<ScanSession> findAllByMember_IdOrderByScannedAtDesc(Long memberId);

    @EntityGraph(attributePaths = {"drugs", "drugs.drug", "drugs.drug.drugInfo"})
    Optional<ScanSession> findByIdAndMember_Id(Long sessionId, Long memberId);
}
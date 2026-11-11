package kr.co.pillguide.backend.api.mydrug.repository;

import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberDrugRepository extends JpaRepository<MemberDrug, Long> {
    Optional<MemberDrug> findByMember_IdAndDrug_DrugId(Long memberId, Long drugId);

    Optional<MemberDrug> findByIdAndMember_Id(Long id, Long memberId);

    Page<MemberDrug> findAllByMember_IdOrderByFirstRegisteredAtDescIdDesc(Long memberId, Pageable pageable);
}

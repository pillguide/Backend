package kr.co.pillguide.backend.api.mydrug.repository;

import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberDrugRepository extends JpaRepository<MemberDrug, Long> {
    @EntityGraph(attributePaths = "drug")
    Optional<MemberDrug> findByMember_IdAndDrug_DrugId(Long memberId, Long drugId);

    @EntityGraph(attributePaths = "drug")
    Optional<MemberDrug> findByIdAndMember_Id(Long id, Long memberId);

    @EntityGraph(attributePaths = "drug")
    Page<MemberDrug> findAllByMember_IdOrderByFirstRegisteredAtDescIdDesc(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "drug")
    List<MemberDrug> findAllByMember_IdAndDrug_DrugIdIn(Long memberId, Collection<Long> drugIds);
}

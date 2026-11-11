package kr.co.pillguide.backend.api.drug.repository;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DrugRepository extends JpaRepository<Drug, Long> {

    Optional<Drug> findByCode(String code);

    @EntityGraph(attributePaths = "drugInfo")
    List<Drug> findAllByCodeIn(Collection<String> codes);

    Page<Drug> findByNameContainingIgnoreCaseOrderByNameAscDrugIdAsc(
            String keyword,
            Pageable pageable
    );

    // 상세 정보까지 함께 조회
    @EntityGraph(attributePaths = "drugInfo")
    Optional<Drug> findDetailByDrugId(Long drugId);
}

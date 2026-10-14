package kr.co.pillguide.backend.api.drug.repository;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DrugRepository extends JpaRepository<Drug, Long> {
    Optional<Drug> findByCode(String code);
}

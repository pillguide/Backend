package kr.co.pillguide.backend.api.drug.repository;

import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugInfoRepository extends JpaRepository<DrugInfo, Long> {
}

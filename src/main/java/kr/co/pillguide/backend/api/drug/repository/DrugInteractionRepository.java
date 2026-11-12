package kr.co.pillguide.backend.api.drug.repository;

import kr.co.pillguide.backend.api.drug.entity.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {
    Optional<DrugInteraction> findByLowDrug_DrugIdAndHighDrug_DrugId(Long lowDrugId, Long highDrugId);

    List<DrugInteraction> findByLowDrug_DrugIdInAndHighDrug_DrugIdIn(
            Collection<Long> lowDrugIds, Collection<Long> highDrugIds);
}

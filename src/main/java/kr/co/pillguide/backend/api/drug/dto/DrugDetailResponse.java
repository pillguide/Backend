package kr.co.pillguide.backend.api.drug.dto;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.image.RepresentativeImage;

public record DrugDetailResponse(
        Long drugId,
        String name,
        String imageUrl,
        String effect,
        String sideEffects,
        String dosageMethod,
        String storageMethod,
        DrugReferenceInfo reference
) {

    public static DrugDetailResponse from(Drug drug, RepresentativeImage image) {
        DrugInfo info = drug.getDrugInfo();

        return new DrugDetailResponse(
                drug.getDrugId(),
                drug.getName(),
                image.url(),
                info != null ? info.getEffect() : null,
                info != null ? info.getSideEffects() : null,
                info != null ? info.getDosageMethod() : null,
                info != null ? info.getStorageMethod() : null,
                DrugReferenceInfo.from(drug, image)
        );
    }
}

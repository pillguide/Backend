package kr.co.pillguide.backend.api.drug.dto;

public record ScanDrugDetailResponse(
        Long drugId,
        String name,
        String code,
        String imageUrl,
        String effect,
        String dosageMethod,
        String storageMethod,
        String sideEffects
) {
}
package kr.co.pillguide.backend.api.drug.dto;

public record ScanDrugSummaryResponse(
        Long drugId,
        String name,
        String code,
        String imageUrl
) {
}
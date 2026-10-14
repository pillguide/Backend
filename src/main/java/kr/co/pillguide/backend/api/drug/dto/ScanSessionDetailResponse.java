package kr.co.pillguide.backend.api.drug.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScanSessionDetailResponse(
        Long sessionId,
        LocalDateTime scannedAt,
        List<ScanDrugSummaryResponse> drugs
) {
}
package kr.co.pillguide.backend.api.drug.dto;

import kr.co.pillguide.backend.api.drug.reference.ReferenceSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ScanSessionDetailResponse(
        Long sessionId,
        LocalDateTime scannedAt,
        int drugCount,
        List<ScanDrugSummaryResponse> drugs,
        boolean legacy,
        UUID requestId,
        ImageSize imageSize,
        String modelSha256,
        String mappingSha256,
        String referenceVersion,
        ReferenceSnapshot.Combination combination,
        boolean userImageAvailable,
        String imageComparisonStatus
) {
    public record ImageSize(int width, int height) { }

    public ScanSessionDetailResponse(Long sessionId, LocalDateTime scannedAt,
                                     List<ScanDrugSummaryResponse> drugs) {
        this(sessionId, scannedAt, drugs.size(), drugs, true, null, null, null, null, null,
                null, false, "NOT_READY");
    }
}

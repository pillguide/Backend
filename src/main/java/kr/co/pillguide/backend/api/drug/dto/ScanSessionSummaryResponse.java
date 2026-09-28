package kr.co.pillguide.backend.api.drug.dto;

import java.time.LocalDateTime;

public record ScanSessionSummaryResponse(
        Long sessionId,
        LocalDateTime scannedAt,
        int drugCount
) {
}
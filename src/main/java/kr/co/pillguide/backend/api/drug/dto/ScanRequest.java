package kr.co.pillguide.backend.api.drug.dto;

import java.util.List;

public record ScanRequest(
        List<String> itemSeqList
) {
}
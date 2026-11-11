package kr.co.pillguide.backend.api.drug.reference;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

/** Detached values from one committed reference generation; never includes managed entities. */
public record ReferenceSnapshot(String version, LocalDate sourceAsOf, boolean refreshDelayed,
                                Map<String, ReferencedDrug> drugs, Combination combination) {
    public ReferenceSnapshot { drugs = Map.copyOf(drugs); }

    public record ReferencedDrug(Long drugId, String itemSeq, String name,
                                 String infoStatus, String authorizationStatus) { }

    public record Combination(int uniqueDrugCount, int pairCount, int confirmedContraindicationCount,
                               List<Match> matches,
                               @Schema(description = "승인된 4쌍 목록에서의 비교 범위. 미일치는 안전 판정이 아님",
                                       allowableValues = {"REVIEWED_LIST_ONLY", "REFERENCE_DATA_PARTIAL"}) String coverageStatus,
                               LocalDate sourceAsOf,
                               boolean refreshDelayed) {
        public Combination { matches = List.copyOf(matches); }
    }

    public record Match(Long firstDrugId, Long secondDrugId, String firstItemSeq, String secondItemSeq,
                        String firstName, String secondName, String evidenceType, String officialReason,
                        String summary, String sourceHash, LocalDate sourceAsOf,
                        List<Map<String, Object>> evidence) {
        public Match { evidence = List.copyOf(evidence); }
    }
}

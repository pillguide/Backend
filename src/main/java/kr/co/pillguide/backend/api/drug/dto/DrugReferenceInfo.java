package kr.co.pillguide.backend.api.drug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Current reference metadata shared by drug detail and owned scan detail. */
public record DrugReferenceInfo(
        @Schema(description = "식약처 품목번호(item_seq)") String code,
        String modelName,
        String classificationCode,
        String classificationName,
        String ingredientDisplay,
        @Schema(description = "공식 출처 확보 정도. 복용 안전 판정이 아님",
                allowableValues = {"COMPLETE", "PARTIAL", "NO_OFFICIAL_RECORD"}) String infoStatus,
        @Schema(description = "허가 자료 상태. 보유 알약의 유효기간이나 복용 가능 판정이 아님",
                allowableValues = {"NORMAL", "ABNORMAL", "UNKNOWN", "NO_OFFICIAL_RECORD"}) String authorizationStatus,
        LocalDate sourceAsOf,
        LocalDateTime sourceFetchedAt,
        String representativeImageStatus
) {
    public static DrugReferenceInfo from(Drug drug) {
        return new DrugReferenceInfo(drug.getCode(), drug.getModelName(), drug.getClassificationCode(),
                drug.getClassificationName(), drug.getIngredientDisplay(), drug.getInfoStatus(),
                drug.getAuthorizationStatus(), drug.getSourceAsOf(),
                drug.getDrugInfo() == null ? null : drug.getDrugInfo().getSourceFetchedAt(), "NOT_READY");
    }
}

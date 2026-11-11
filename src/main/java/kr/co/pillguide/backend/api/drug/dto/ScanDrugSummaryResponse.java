package kr.co.pillguide.backend.api.drug.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ScanDrugSummaryResponse(
        Long drugId,
        String name,
        @Schema(description = "식약처 품목번호(item_seq), 문자열 식별자") String code,
        String imageUrl,
        @Schema(description = "내 약 선택 저장에 사용하는 영구 검출 ID, 과거 JSON 이력은 null") Long detectionId,
        @Schema(description = "현재 사진의 1부터 시작하는 검출 순번") Integer detectionIndex,
        @Schema(description = "0~1 모델 점수. 정답 확률이 아님", minimum = "0", maximum = "1") Double confidence,
        @Schema(description = "EXIF 방향을 적용한 사진 기준 정규화 [x1,y1,x2,y2]") List<Double> bboxXyxy,
        Boolean recognitionMayBeDegraded,
        String infoStatus,
        String authorizationStatus,
        @Schema(description = "데이터셋 대표 이미지 준비 상태. 사용자 원본 사진과 구분", allowableValues = {"NOT_READY", "READY"}) String representativeImageStatus
) {
    public ScanDrugSummaryResponse(Long drugId, String name, String code, String imageUrl) {
        this(drugId, name, code, imageUrl, null, null, null, null, null, null, null, null);
    }
}

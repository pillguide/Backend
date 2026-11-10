package kr.co.pillguide.backend.api.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.pillguide.backend.api.drug.dto.DrugDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.DrugSearchResponse;
import kr.co.pillguide.backend.api.drug.service.DrugSearchService;
import kr.co.pillguide.backend.common.response.ApiResponse;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Drug Search", description = "약 검색 및 상세 정보 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drugs")
public class DrugSearchController {

    private final DrugSearchService drugSearchService;

    @Operation(
            summary = "알약명 검색",
            description = "DB에 저장된 약을 이름으로 부분 검색합니다. 페이지는 0부터 시작합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<DrugSearchResponse>> search(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        DrugSearchResponse response = drugSearchService.search(
                keyword,
                page,
                size
        );

        return ApiResponse.success(
                SuccessStatus.DRUG_SEARCH_SUCCESS,
                response
        );
    }

    @Operation(
            summary = "약 상세 정보 조회",
            description = "약 ID로 이름, 이미지, 효능, 부작용, 복용법, 보관법을 조회합니다."
    )
    @GetMapping("/{drugId}")
    public ResponseEntity<ApiResponse<DrugDetailResponse>> getDetail(
            @PathVariable("drugId") Long drugId
    ) {
        DrugDetailResponse response =
                drugSearchService.getDetail(drugId);

        return ApiResponse.success(
                SuccessStatus.DRUG_DETAIL_GET_SUCCESS,
                response
        );
    }
}
package kr.co.pillguide.backend.api.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.co.pillguide.backend.api.drug.service.DrugService;
import kr.co.pillguide.backend.common.response.SuccessStatus;
import kr.co.pillguide.backend.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drugs")
public class DrugController {

    private final DrugService drugService;

    @Operation(
            summary = "약 정보 등록 API",
            description = "itemSeq로 외부 API 조회 후 DB 저장"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "데이터 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록됨")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> saveDrug(@RequestParam String itemSeq) {

        drugService.saveDrugByItemSeq(itemSeq);

        return ApiResponse.successOnly(SuccessStatus.DRUG_CREATE_SUCCESS);
    }
}
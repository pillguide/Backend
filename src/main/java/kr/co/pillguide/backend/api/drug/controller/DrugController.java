package kr.co.pillguide.backend.api.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.co.pillguide.backend.api.drug.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drugs")
public class DrugController {

    private final DrugService drugService;

    @Operation(
            summary = "약 정보 등록 API",
            description = "식별 번호(itemSeq)를 이용해 외부 시스템에서 약 정보를 가져와 데이터베이스에 저장"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 완료"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 itemSeq (데이터 없음)"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 약품")
    })
    @PostMapping("/register")
    public String saveDrug(@RequestParam String itemSeq) throws Exception {
        drugService.saveDrugByItemSeq(itemSeq);
        return "저장 완료";
    }
}

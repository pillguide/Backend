package kr.co.pillguide.backend.api.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
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
            description = "식별 번호(itemSeq)를 이용해 외부 시스템에서 약 정보를 가져와 데이터베이스에 저장합니다. <br>" +
                    "필수 정보: 품목 일련번호(itemSeq)"
    )
    @PostMapping("/register")
    public String saveDrug(@RequestParam String itemSeq) throws Exception {
        drugService.saveDrugByItemSeq(itemSeq);
        return "저장 완료";
    }
}

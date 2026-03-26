package kr.co.pillguide.backend.api.drug.controller;

import kr.co.pillguide.backend.api.drug.service.DrugApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class DrugApiTestController {

    private final DrugApiClient drugApiClient;

    @GetMapping("/api/v1/test/drugs")
    public String getDrugByItemSeq(@RequestParam String itemSeq) {
        return drugApiClient.getDrugByItemSeq(itemSeq);
    }
}
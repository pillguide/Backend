package kr.co.pillguide.backend.api.mydrug.dto;

import kr.co.pillguide.backend.api.drug.dto.DrugDetailResponse;

import java.time.LocalDateTime;

public record MyDrugDetailResponse(Long memberDrugId, LocalDateTime firstRegisteredAt,
                                   DrugDetailResponse drug) { }

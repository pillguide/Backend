package kr.co.pillguide.backend.api.drug.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DrugApiResponseDto {
    private DrugHeader header;
    private DrugBody body;
}

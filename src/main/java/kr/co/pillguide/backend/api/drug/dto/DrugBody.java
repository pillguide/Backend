package kr.co.pillguide.backend.api.drug.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DrugBody {
    private List<DrugItemDto> items;
}

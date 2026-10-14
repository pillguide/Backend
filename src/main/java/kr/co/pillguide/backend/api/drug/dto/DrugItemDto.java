package kr.co.pillguide.backend.api.drug.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DrugItemDto {

    private String itemName;
    private String itemSeq;
    private String itemImage;

    private String efcyQesitm;        // 효능
    private String useMethodQesitm;   // 복용법
    private String depositMethodQesitm; // 보관법
    private String seQesitm;          // 부작용
}

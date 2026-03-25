package kr.co.pillguide.backend.api.drug.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.drug.dto.DrugApiResponseDto;
import kr.co.pillguide.backend.api.drug.dto.DrugItemDto;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugRepository drugRepository;
    private final DrugApiClient drugApiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveDrugByItemSeq(String itemSeq) throws Exception {

        // 1. API 호출
        String response = drugApiClient.getDrugByItemSeq(itemSeq);

        // 2. JSON → DTO 변환
        DrugApiResponseDto dto =
                objectMapper.readValue(response, DrugApiResponseDto.class);

        DrugItemDto item = dto.getBody().getItems().get(0);

        // 3. 중복 체크
        if (drugRepository.findByCode(item.getItemSeq()).isPresent()) {
            return;
        }

        // 4. Drug 생성
        Drug drug = new Drug(
                item.getItemName(),
                item.getItemSeq(),
                item.getItemImage()
        );

        // 5. DrugInfo 생성
        DrugInfo drugInfo = new DrugInfo(
                drug,
                item.getUseMethodQesitm(),
                item.getDepositMethodQesitm(),
                item.getSeQesitm(),
                item.getEfcyQesitm()
        );

        drug.setDrugInfo(drugInfo);

        // 6. 저장
        drugRepository.save(drug);
    }
}

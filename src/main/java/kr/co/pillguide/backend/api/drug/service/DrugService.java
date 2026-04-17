package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.dto.DrugApiResponseDto;
import kr.co.pillguide.backend.api.drug.dto.DrugItemDto;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.common.exception.ConflictException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugRepository drugRepository;
    private final DrugEasyApiClient drugEasyApiClient;

    @Transactional
    public void saveDrugByItemSeq(String itemSeq) {

        // 1. 중복 체크
        if (drugRepository.findByCode(itemSeq).isPresent()) {
            throw new ConflictException("이미 등록된 약품 코드입니다: " + itemSeq);
        }

        // 2. API 호출
        DrugApiResponseDto dto = drugEasyApiClient.getDrugByItemSeq(itemSeq);

        // API 성공 체크
        if (dto == null || dto.getHeader() == null || !"00".equals(dto.getHeader().getResultCode())) {
            throw new RuntimeException("API 호출 실패: " +
                    (dto != null && dto.getHeader() != null ? dto.getHeader().getResultMsg() : "응답 없음"));
        }

        // 데이터 체크
        if (dto.getBody() == null ||
                dto.getBody().getItems() == null ||
                dto.getBody().getItems().isEmpty()) {

            throw new NotFoundException("해당 코드(" + itemSeq + ")로 조회된 약 정보가 없습니다.");
        }

        DrugItemDto item = dto.getBody().getItems().get(0);

        // 4. Drug 엔티티 생성
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

package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.dto.DrugDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.DrugSearchResponse;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DrugSearchService {

    private final DrugRepository drugRepository;

    public DrugSearchResponse search(
            String keyword,
            int page,
            int size
    ) {
        if (keyword == null || keyword.isBlank()) {
            throw new BadRequestException(
                    "검색할 약 이름을 입력해주세요."
            );
        }

        String normalizedKeyword = keyword.strip();

        if (normalizedKeyword.length() > 100) {
            throw new BadRequestException(
                    "검색어는 100자 이하로 입력해주세요."
            );
        }

        if (page < 0) {
            throw new BadRequestException(
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > 100) {
            throw new BadRequestException(
                    "조회 개수는 1 이상 100 이하여야 합니다."
            );
        }

        Page<Drug> result = drugRepository
                .findByNameContainingIgnoreCaseOrderByNameAscDrugIdAsc(
                        normalizedKeyword,
                        PageRequest.of(page, size)
                );

        return DrugSearchResponse.from(result);
    }

    public DrugDetailResponse getDetail(Long drugId) {
        if (drugId == null || drugId < 1) {
            throw new BadRequestException(
                    "올바른 약 ID를 입력해주세요."
            );
        }

        Drug drug = drugRepository.findDetailByDrugId(drugId)
                .orElseThrow(() -> new NotFoundException(
                        "해당 약 정보를 찾을 수 없습니다."
                ));

        return DrugDetailResponse.from(drug);
    }
}

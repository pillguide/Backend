package kr.co.pillguide.backend.api.mydrug.dto;

import org.springframework.data.domain.Page;
import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;

import java.util.List;

public record MyDrugPageResponse(List<MyDrugItemResponse> items, int page, int size,
                                 long totalElements, int totalPages, boolean hasNext) {
    public static MyDrugPageResponse from(Page<MemberDrug> relations) {
        return new MyDrugPageResponse(relations.getContent().stream().map(MyDrugItemResponse::from).toList(),
                relations.getNumber(), relations.getSize(), relations.getTotalElements(),
                relations.getTotalPages(), relations.hasNext());
    }
}

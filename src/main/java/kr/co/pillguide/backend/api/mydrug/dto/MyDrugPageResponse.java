package kr.co.pillguide.backend.api.mydrug.dto;

import org.springframework.data.domain.Page;
import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;

import java.util.List;
import java.util.function.Function;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.image.RepresentativeImage;

public record MyDrugPageResponse(List<MyDrugItemResponse> items, int page, int size,
                                 long totalElements, int totalPages, boolean hasNext) {
    public static MyDrugPageResponse from(Page<MemberDrug> relations, Function<Drug, RepresentativeImage> images) {
        return new MyDrugPageResponse(relations.getContent().stream()
                .map(relation -> MyDrugItemResponse.from(relation, images.apply(relation.getDrug()))).toList(),
                relations.getNumber(), relations.getSize(), relations.getTotalElements(),
                relations.getTotalPages(), relations.hasNext());
    }
}

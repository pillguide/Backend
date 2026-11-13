package kr.co.pillguide.backend.api.drug.dto;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import kr.co.pillguide.backend.api.drug.image.RepresentativeImage;

public record DrugSearchResponse(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static DrugSearchResponse from(Page<Drug> result, Function<Drug, RepresentativeImage> images) {
        return new DrugSearchResponse(
                result.getContent().stream()
                        .map(drug -> new Item(drug.getDrugId(), drug.getName(), images.apply(drug).url()))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    public record Item(
            Long drugId,
            String name,
            String imageUrl
    ) {

        public static Item from(Drug drug) {
            return new Item(
                    drug.getDrugId(),
                    drug.getName(),
                    drug.getImageUrl()
            );
        }
    }
}

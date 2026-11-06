package kr.co.pillguide.backend.api.drug.dto;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import org.springframework.data.domain.Page;

import java.util.List;

public record DrugSearchResponse(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static DrugSearchResponse from(Page<Drug> result) {
        return new DrugSearchResponse(
                result.getContent().stream()
                        .map(Item::from)
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
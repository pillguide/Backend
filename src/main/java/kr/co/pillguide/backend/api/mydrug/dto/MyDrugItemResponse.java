package kr.co.pillguide.backend.api.mydrug.dto;

import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;
import kr.co.pillguide.backend.api.drug.image.RepresentativeImage;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MyDrugItemResponse(Long memberDrugId, LocalDateTime firstRegisteredAt,
                                 Long drugId, String name, String code, String imageUrl,
                                 String infoStatus, String authorizationStatus,
                                 LocalDate sourceAsOf, String representativeImageStatus) {
    public static MyDrugItemResponse from(MemberDrug relation, RepresentativeImage image) {
        var drug = relation.getDrug();
        return new MyDrugItemResponse(relation.getId(), relation.getFirstRegisteredAt(),
                drug.getDrugId(), drug.getName(), drug.getCode(), image.url(),
                drug.getInfoStatus(), drug.getAuthorizationStatus(), drug.getSourceAsOf(), image.status());
    }
}

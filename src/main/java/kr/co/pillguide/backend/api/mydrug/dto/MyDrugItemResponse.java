package kr.co.pillguide.backend.api.mydrug.dto;

import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MyDrugItemResponse(Long memberDrugId, LocalDateTime firstRegisteredAt,
                                 Long drugId, String name, String code, String imageUrl,
                                 String infoStatus, String authorizationStatus,
                                 LocalDate sourceAsOf, String representativeImageStatus) {
    public static MyDrugItemResponse from(MemberDrug relation) {
        var drug = relation.getDrug();
        return new MyDrugItemResponse(relation.getId(), relation.getFirstRegisteredAt(),
                drug.getDrugId(), drug.getName(), drug.getCode(), drug.getImageUrl(),
                drug.getInfoStatus(), drug.getAuthorizationStatus(), drug.getSourceAsOf(), "NOT_READY");
    }
}

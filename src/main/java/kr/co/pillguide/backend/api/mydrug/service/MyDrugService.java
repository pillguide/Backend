package kr.co.pillguide.backend.api.mydrug.service;

import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.image.RepresentativeImageService;
import kr.co.pillguide.backend.api.drug.service.DrugSearchService;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.service.MemberLocks;
import kr.co.pillguide.backend.api.mydrug.dto.AddMyDrugsRequest;
import kr.co.pillguide.backend.api.mydrug.dto.MyDrugDetailResponse;
import kr.co.pillguide.backend.api.mydrug.dto.MyDrugItemResponse;
import kr.co.pillguide.backend.api.mydrug.dto.MyDrugPageResponse;
import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;
import kr.co.pillguide.backend.api.mydrug.repository.MemberDrugRepository;
import kr.co.pillguide.backend.api.scan.service.ScanSelectionReader;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MyDrugService {
    public record AddOutcome(boolean created, List<MyDrugItemResponse> selected) { }

    private final EntityManager em;
    private final MemberDrugRepository relations;
    private final ScanSelectionReader selections;
    private final DrugSearchService drugDetails;
    private final RepresentativeImageService images;

    @Transactional
    public AddOutcome add(JwtService.TokenClaims claims, AddMyDrugsRequest request) {
        validateSelection(request);
        Member member = MemberLocks.lockAuthenticated(em, claims);
        List<Long> selectedDrugIds = selections.selectedDrugIds(member.getId(), request.scanSessionId(),
                new HashSet<>(request.detectionIds()));
        Map<Long, MemberDrug> existing = new HashMap<>();
        for (MemberDrug relation : relations.findAllByMember_IdAndDrug_DrugIdIn(member.getId(), selectedDrugIds)) {
            existing.put(relation.getDrug().getDrugId(), relation);
        }
        List<MemberDrug> created = new ArrayList<>();
        Map<Long, MemberDrug> selected = new LinkedHashMap<>();
        for (Long drugId : selectedDrugIds) {
            MemberDrug relation = existing.get(drugId);
            if (relation == null) {
                relation = new MemberDrug(member, em.getReference(Drug.class, drugId));
                created.add(relation);
            }
            selected.put(drugId, relation);
        }
        if (!created.isEmpty()) relations.saveAllAndFlush(created);
        return new AddOutcome(!created.isEmpty(), selected.values().stream()
                .map(relation -> MyDrugItemResponse.from(relation, images.resolve(relation.getDrug()))).toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyDrugPageResponse list(Long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("페이지는 0 이상, 조회 개수는 1~100이어야 합니다.");
        }
        return MyDrugPageResponse.from(relations.findAllByMember_IdOrderByFirstRegisteredAtDescIdDesc(
                memberId, PageRequest.of(page, size)), images::resolve);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyDrugDetailResponse detail(Long memberId, Long memberDrugId) {
        MemberDrug relation = relations.findByIdAndMember_Id(memberDrugId, memberId)
                .orElseThrow(MyDrugService::notFound);
        return new MyDrugDetailResponse(relation.getId(), relation.getFirstRegisteredAt(),
                drugDetails.getDetail(relation.getDrug().getDrugId()));
    }

    @Transactional
    public Long delete(JwtService.TokenClaims claims, Long memberDrugId) {
        Member member = MemberLocks.lockAuthenticated(em, claims);
        MemberDrug relation = relations.findByIdAndMember_Id(memberDrugId, member.getId())
                .orElseThrow(MyDrugService::notFound);
        relations.delete(relation);
        relations.flush();
        return memberDrugId;
    }

    private static void validateSelection(AddMyDrugsRequest request) {
        if (request == null || request.scanSessionId() == null || request.scanSessionId() < 1
                || request.detectionIds() == null || request.detectionIds().isEmpty()
                || request.detectionIds().size() > 300
                || request.detectionIds().stream().anyMatch(id -> id == null || id < 1)) {
            throw new BadRequestException("본인 스캔에서 검출 1~300개를 선택해 주세요.");
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("내 약 정보를 찾을 수 없습니다.");
    }
}

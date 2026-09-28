package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.dto.ScanDrugDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanDrugSummaryResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanRequest;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionSummaryResponse;
import kr.co.pillguide.backend.api.drug.entity.*;
import kr.co.pillguide.backend.api.drug.repository.*;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanSessionRepository scanSessionRepository;
    private final DrugRepository drugRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long saveScanSession(Long memberId, ScanRequest request) {

        // 1. Member 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER.getMessage()));

        // 2. 세션 생성
        ScanSession session = new ScanSession(member);

        // 3. 약 리스트 추가
        for (String itemSeq : request.itemSeqList()) {

            Drug drug = drugRepository.findByCode(itemSeq)
                    .orElseThrow(() -> new NotFoundException(
                            ErrorStatus.NOT_FOUND_DRUG.getMessage() + " : " + itemSeq
                    ));

            ScanDrug scanDrug = new ScanDrug(session, drug);
            session.addDrug(scanDrug);
        }

        // 4. 저장
        scanSessionRepository.save(session);

        return session.getId();
    }

    @Transactional(readOnly = true)
    public List<ScanSessionSummaryResponse> getScanSessions(Long memberId) {
        return scanSessionRepository.findAllByMember_IdOrderByScannedAtDesc(memberId).stream()
                .map(session -> new ScanSessionSummaryResponse(
                        session.getId(),
                        session.getScannedAt(),
                        session.getDrugs().size()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScanSessionDetailResponse getScanSession(Long memberId, Long sessionId) {
        ScanSession session = getMemberSession(memberId, sessionId);

        List<ScanDrugSummaryResponse> drugs = session.getDrugs().stream()
                .map(ScanDrug::getDrug)
                .map(drug -> new ScanDrugSummaryResponse(
                        drug.getDrugId(),
                        drug.getName(),
                        drug.getCode(),
                        drug.getImageUrl()
                ))
                .toList();

        return new ScanSessionDetailResponse(session.getId(), session.getScannedAt(), drugs);
    }

    @Transactional(readOnly = true)
    public ScanDrugDetailResponse getScanDrug(Long memberId, Long sessionId, Long drugId) {
        ScanSession session = getMemberSession(memberId, sessionId);

        Drug drug = session.getDrugs().stream()
                .map(ScanDrug::getDrug)
                .filter(sessionDrug -> sessionDrug.getDrugId().equals(drugId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_DRUG.getMessage()));

        DrugInfo drugInfo = drug.getDrugInfo();
        return new ScanDrugDetailResponse(
                drug.getDrugId(),
                drug.getName(),
                drug.getCode(),
                drug.getImageUrl(),
                drugInfo != null ? drugInfo.getEffect() : null,
                drugInfo != null ? drugInfo.getDosageMethod() : null,
                drugInfo != null ? drugInfo.getStorageMethod() : null,
                drugInfo != null ? drugInfo.getSideEffects() : null
        );
    }

    private ScanSession getMemberSession(Long memberId, Long sessionId) {
        return scanSessionRepository.findByIdAndMember_Id(sessionId, memberId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_SCAN_SESSION.getMessage()));
    }
}
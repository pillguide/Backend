package kr.co.pillguide.backend.api.drug.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.drug.dto.ScanDrugDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanDrugSummaryResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionDetailResponse;
import kr.co.pillguide.backend.api.drug.dto.ScanSessionSummaryResponse;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.entity.ScanDrug;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.reference.ReferenceSnapshot;
import kr.co.pillguide.backend.api.drug.repository.ScanSessionRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScanService {
    private final ScanSessionRepository sessions;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<ScanSessionSummaryResponse> getScanSessions(Long memberId) {
        return sessions.findAllByMember_IdOrderByScannedAtDesc(memberId).stream()
                .map(session -> new ScanSessionSummaryResponse(session.getId(), session.getScannedAt(),
                        session.getDrugs().size()))
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ScanSessionDetailResponse getScanSession(Long memberId, Long sessionId) {
        return detail(getMemberSession(memberId, sessionId));
    }

    public ScanSessionDetailResponse detail(ScanSession session) {
        List<ScanDrugSummaryResponse> drugs = session.getDrugs().stream().map(this::summary).toList();
        if (session.isLegacy()) return new ScanSessionDetailResponse(session.getId(), session.getScannedAt(), drugs);
        return new ScanSessionDetailResponse(session.getId(), session.getScannedAt(), drugs.size(), drugs,
                false, session.getRequestId(),
                new ScanSessionDetailResponse.ImageSize(session.getImageWidth(), session.getImageHeight()),
                session.getModelSha256(), session.getMappingSha256(), session.getReferenceVersion(),
                combination(session.getInteractionSnapshot()), false, "NOT_READY");
    }

    private ScanDrugSummaryResponse summary(ScanDrug detection) {
        Drug drug = detection.getDrug();
        if (detection.getDetectionIndex() == null) {
            return new ScanDrugSummaryResponse(drug.getDrugId(), drug.getName(), drug.getCode(), drug.getImageUrl());
        }
        return new ScanDrugSummaryResponse(drug.getDrugId(), detection.getNameSnapshot(), drug.getCode(), null,
                detection.getId(), detection.getDetectionIndex(), detection.getConfidence(),
                List.of(detection.getBboxX1(), detection.getBboxY1(),
                        detection.getBboxX2(), detection.getBboxY2()),
                detection.getRecognitionMayBeDegraded(), detection.getInfoStatusSnapshot(),
                detection.getAuthorizationStatusSnapshot(), "NOT_READY");
    }

    private ReferenceSnapshot.Combination combination(String json) {
        try { return mapper.readValue(json, ReferenceSnapshot.Combination.class); }
        catch (Exception ex) { throw new IllegalStateException("Stored scan snapshot is invalid"); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ScanDrugDetailResponse getScanDrug(Long memberId, Long sessionId, Long drugId) {
        ScanSession session = getMemberSession(memberId, sessionId);
        Drug drug = session.getDrugs().stream().map(ScanDrug::getDrug)
                .filter(candidate -> candidate.getDrugId().equals(drugId))
                .findFirst().orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_DRUG.getMessage()));
        DrugInfo info = drug.getDrugInfo();
        return new ScanDrugDetailResponse(drug.getDrugId(), drug.getName(), drug.getCode(),
                session.isLegacy() ? drug.getImageUrl() : null,
                info == null ? null : info.getEffect(), info == null ? null : info.getDosageMethod(),
                info == null ? null : info.getStorageMethod(), info == null ? null : info.getSideEffects(),
                session.isLegacy() ? null : "NOT_READY");
    }

    private ScanSession getMemberSession(Long memberId, Long sessionId) {
        return sessions.findByIdAndMember_Id(sessionId, memberId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_SCAN_SESSION.getMessage()));
    }
}

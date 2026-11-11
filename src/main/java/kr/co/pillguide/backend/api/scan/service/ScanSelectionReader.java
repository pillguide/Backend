package kr.co.pillguide.backend.api.scan.service;

import kr.co.pillguide.backend.api.drug.entity.ScanDrug;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.repository.ScanSessionRepository;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves a member's selected detections to product IDs without exposing arbitrary drug-ID input. */
@Service
@RequiredArgsConstructor
public class ScanSelectionReader {
    private final ScanSessionRepository sessions;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> selectedDrugIds(Long memberId, Long sessionId, Set<Long> detectionIds) {
        ScanSession session = sessions.findByIdAndMember_Id(sessionId, memberId)
                .orElseThrow(ScanSelectionReader::notFound);
        Set<Long> available = session.getDrugs().stream().map(ScanDrug::getId).collect(Collectors.toSet());
        if (!available.containsAll(detectionIds)) throw notFound();
        return session.getDrugs().stream().filter(detection -> detectionIds.contains(detection.getId()))
                .map(detection -> detection.getDrug().getDrugId()).distinct().toList();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("선택한 스캔 결과를 찾을 수 없습니다.");
    }
}

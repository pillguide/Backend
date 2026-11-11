package kr.co.pillguide.backend.api.scan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.ScanDrug;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.reference.ReferenceSnapshot;
import kr.co.pillguide.backend.api.drug.repository.ScanSessionRepository;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.service.MemberLocks;
import kr.co.pillguide.backend.api.scan.exception.ScanException;
import kr.co.pillguide.backend.api.scan.inference.AiPredictionResult;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** The only write transaction in the scan flow; no image bytes or network calls enter it. */
@Service
@RequiredArgsConstructor
public class ScanWriteService {
    public record WriteResult(Long sessionId, boolean created) { }

    private final EntityManager em;
    private final ScanSessionRepository sessions;
    private final ObjectMapper mapper;

    @Transactional
    public Optional<Long> findReplay(JwtService.TokenClaims claims, UUID requestId, String requestHash) {
        MemberLocks.lockAuthenticated(em, claims);
        return existing(claims.memberId(), requestId, requestHash).map(ScanSession::getId);
    }

    @Transactional
    public WriteResult persist(JwtService.TokenClaims claims, UUID requestId, String imageHash,
                               int imageWidth, int imageHeight,
                               AiPredictionResult result, ReferenceSnapshot reference) {
        Member member = MemberLocks.lockAuthenticated(em, claims);
        Optional<ScanSession> previous = existing(claims.memberId(), requestId, imageHash);
        if (previous.isPresent()) return new WriteResult(previous.get().getId(), false);

        ScanSession session = new ScanSession(member, requestId, imageHash, imageWidth, imageHeight,
                result.modelSha256(), result.mappingSha256(), reference.version(), snapshotJson(reference));
        for (AiPredictionResult.Prediction prediction : result.predictions()) {
            ReferenceSnapshot.ReferencedDrug referenced = reference.drugs().get(prediction.itemSeq());
            Drug drug = em.getReference(Drug.class, referenced.drugId());
            var box = prediction.bboxXyxy();
            session.addDrug(new ScanDrug(session, drug, prediction.detectionIndex(), prediction.confidence(),
                    box.get(0), box.get(1), box.get(2), box.get(3),
                    prediction.recognitionMayBeDegraded(), referenced.name(), referenced.infoStatus(),
                    referenced.authorizationStatus()));
        }
        sessions.saveAndFlush(session);
        return new WriteResult(session.getId(), true);
    }

    private Optional<ScanSession> existing(Long memberId, UUID requestId, String requestHash) {
        Optional<ScanSession> previous = sessions.findByMember_IdAndRequestId(memberId, requestId);
        if (previous.isPresent() && !requestHash.equals(previous.get().getRequestHash())) {
            throw new ScanException(HttpStatus.CONFLICT, "같은 요청 ID에 다른 사진이 사용되었습니다.", null);
        }
        return previous;
    }

    private String snapshotJson(ReferenceSnapshot reference) {
        try { return mapper.writeValueAsString(reference.combination()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize reference snapshot"); }
    }
}

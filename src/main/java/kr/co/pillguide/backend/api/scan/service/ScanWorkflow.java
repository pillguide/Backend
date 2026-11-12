package kr.co.pillguide.backend.api.scan.service;

import kr.co.pillguide.backend.api.drug.dto.ScanSessionDetailResponse;
import kr.co.pillguide.backend.api.drug.reference.ReferenceReadService;
import kr.co.pillguide.backend.api.drug.reference.ReferenceSnapshot;
import kr.co.pillguide.backend.api.drug.service.ScanService;
import kr.co.pillguide.backend.api.scan.exception.ScanException;
import kr.co.pillguide.backend.api.scan.inference.AiClient;
import kr.co.pillguide.backend.api.scan.inference.AiPredictionResult;
import kr.co.pillguide.backend.api.scan.inference.PhotoInput;
import kr.co.pillguide.backend.api.scan.inference.PhotoValidator;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Orchestrates validation, inference, reference read, then a separate short write. */
@Service
@RequiredArgsConstructor
public class ScanWorkflow {
    public record Outcome(boolean created, ScanSessionDetailResponse result) { }

    private final PhotoValidator photoValidator;
    private final AiClient aiClient;
    private final ReferenceReadService references;
    private final ScanWriteService writer;
    private final ScanService scans;

    public Outcome analyze(JwtService.TokenClaims claims, UUID requestId, MultipartFile file) {
        if (requestId == null) throw new ScanException(HttpStatus.BAD_REQUEST, "요청 ID가 필요합니다.", null);
        PhotoInput photo = photoValidator.validate(file);
        var replay = writer.findReplay(claims, requestId, photo.sha256());
        if (replay.isPresent()) {
            return new Outcome(false, scans.getScanSession(claims.memberId(), replay.get()));
        }

        // The remote inference call runs with no database transaction or member lock held.
        AiPredictionResult prediction = aiClient.predict(photo);
        Set<String> uniqueItems = prediction.predictions().stream()
                .map(AiPredictionResult.Prediction::itemSeq).collect(Collectors.toSet());
        ReferenceSnapshot reference = references.read(uniqueItems);
        if (!reference.drugs().keySet().equals(uniqueItems)) {
            throw new ScanException(HttpStatus.SERVICE_UNAVAILABLE, "약품 참조자료를 확인할 수 없습니다.", null);
        }
        var stored = writer.persist(claims, requestId, photo.sha256(), photo.width(), photo.height(),
                prediction, reference);
        return new Outcome(stored.created(), scans.getScanSession(claims.memberId(), stored.sessionId()));
    }
}

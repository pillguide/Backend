package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scan_drug", uniqueConstraints =
        @UniqueConstraint(name = "uk_scan_drug_session_index", columnNames = {"scan_session_id", "detection_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanDrug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_session_id")
    private ScanSession scanSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drug_drug_id")
    private Drug drug;

    private Integer detectionIndex;
    private Double confidence;
    private Double bboxX1;
    private Double bboxY1;
    private Double bboxX2;
    private Double bboxY2;
    private Boolean recognitionMayBeDegraded;
    private String nameSnapshot;
    private String infoStatusSnapshot;
    private String authorizationStatusSnapshot;

    public ScanDrug(ScanSession scanSession, Drug drug) {
        this(scanSession, drug, true);
    }

    private ScanDrug(ScanSession scanSession, Drug drug, boolean legacy) {
        if (scanSession == null || drug == null) {
            throw new IllegalArgumentException("Scan session and drug are required");
        }
        if (scanSession.isLegacy() != legacy) {
            throw new IllegalArgumentException("Detection and scan session types must match");
        }
        this.scanSession = scanSession;
        this.drug = drug;
    }

    public ScanDrug(ScanSession scanSession, Drug drug, int detectionIndex, double confidence,
                    double bboxX1, double bboxY1, double bboxX2, double bboxY2,
                    boolean recognitionMayBeDegraded, String nameSnapshot,
                    String infoStatusSnapshot, String authorizationStatusSnapshot) {
        this(scanSession, drug, false);
        if (detectionIndex < 0 || !inUnitRange(confidence)
                || !inUnitRange(bboxX1) || !inUnitRange(bboxY1)
                || !inUnitRange(bboxX2) || !inUnitRange(bboxY2)
                || bboxX1 >= bboxX2 || bboxY1 >= bboxY2
                || nameSnapshot == null || nameSnapshot.isBlank()
                || infoStatusSnapshot == null || infoStatusSnapshot.isBlank()
                || authorizationStatusSnapshot == null || authorizationStatusSnapshot.isBlank()) {
            throw new IllegalArgumentException("Complete detection metadata is required");
        }
        this.detectionIndex = detectionIndex;
        this.confidence = confidence;
        this.bboxX1 = bboxX1;
        this.bboxY1 = bboxY1;
        this.bboxX2 = bboxX2;
        this.bboxY2 = bboxY2;
        this.recognitionMayBeDegraded = recognitionMayBeDegraded;
        this.nameSnapshot = nameSnapshot;
        this.infoStatusSnapshot = infoStatusSnapshot;
        this.authorizationStatusSnapshot = authorizationStatusSnapshot;
    }

    private static boolean inUnitRange(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}

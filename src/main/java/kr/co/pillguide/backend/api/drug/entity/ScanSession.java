package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.api.member.entity.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scan_session", uniqueConstraints =
        @UniqueConstraint(name = "uk_scan_session_member_request", columnNames = {"member_id", "request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private LocalDateTime scannedAt;

    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "request_hash", length = 64)
    private String requestHash;
    private Integer imageWidth;
    private Integer imageHeight;
    @Column(length = 64)
    private String modelSha256;
    @Column(length = 64)
    private String mappingSha256;
    private String referenceVersion;
    @Column(columnDefinition = "text")
    private String interactionSnapshot;

    @OneToMany(mappedBy = "scanSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("detectionIndex ASC, id ASC")
    private List<ScanDrug> drugs = new ArrayList<>();

    public ScanSession(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member is required");
        }
        this.member = member;
        this.scannedAt = LocalDateTime.now();
    }


    public ScanSession(Member member, UUID requestId, String requestHash,
                       int imageWidth, int imageHeight, String modelSha256,
                       String mappingSha256, String referenceVersion,
                       String interactionSnapshot) {
        this(member);
        if (requestId == null || !isSha256(requestHash) || imageWidth <= 0 || imageHeight <= 0
                || !isSha256(modelSha256) || !isSha256(mappingSha256)
                || referenceVersion == null || referenceVersion.isBlank()
                || interactionSnapshot == null || interactionSnapshot.isBlank()) {
            throw new IllegalArgumentException("Complete scan metadata is required");
        }
        this.requestId = requestId;
        this.requestHash = requestHash;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.modelSha256 = modelSha256;
        this.mappingSha256 = mappingSha256;
        this.referenceVersion = referenceVersion;
        this.interactionSnapshot = interactionSnapshot;
    }

    public boolean isLegacy() {
        return requestId == null;
    }

    public List<ScanDrug> getDrugs() {
        return Collections.unmodifiableList(drugs);
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    public void addDrug(ScanDrug drug) {
        if (drug == null || drug.getScanSession() != this) {
            throw new IllegalArgumentException("Detection must belong to this scan session");
        }
        drugs.add(drug);
    }
}

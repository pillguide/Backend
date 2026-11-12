package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDate;

@Entity
@Table(name = "drug_interaction", uniqueConstraints =
        @UniqueConstraint(name = "uk_drug_interaction_pair", columnNames = {"low_drug_id", "high_drug_id"}))
@Check(constraints = "low_drug_id < high_drug_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "low_drug_id", nullable = false)
    private Drug lowDrug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "high_drug_id", nullable = false)
    private Drug highDrug;

    @Column(nullable = false, columnDefinition = "text")
    private String evidenceJson;

    @Column(columnDefinition = "text")
    private String officialReason;

    @Column(columnDefinition = "text")
    private String reviewedSummary;

    @Column(nullable = false, length = 64)
    private String sourceHash;

    @Column(length = 64)
    private String summarySourceHash;

    @Column(nullable = false)
    private LocalDate sourceAsOf;

    @Column(nullable = false)
    private String referenceVersion;

    public DrugInteraction(Drug lowDrug, Drug highDrug, String evidenceJson,
                           String officialReason, String reviewedSummary, String sourceHash,
                           String summarySourceHash, LocalDate sourceAsOf,
                           String referenceVersion) {
        if (lowDrug == null || highDrug == null || lowDrug.getDrugId() == null
                || highDrug.getDrugId() == null || lowDrug.getDrugId() >= highDrug.getDrugId()
                || evidenceJson == null || evidenceJson.isBlank()
                || !isSha256(sourceHash) || sourceAsOf == null
                || referenceVersion == null || referenceVersion.isBlank()
                || (reviewedSummary == null) != (summarySourceHash == null)
                || (summarySourceHash != null && !sourceHash.equals(summarySourceHash))) {
            throw new IllegalArgumentException("Ordered persisted drugs and complete interaction evidence are required");
        }
        this.lowDrug = lowDrug;
        this.highDrug = highDrug;
        this.evidenceJson = evidenceJson;
        this.officialReason = officialReason;
        this.reviewedSummary = reviewedSummary;
        this.sourceHash = sourceHash;
        this.summarySourceHash = summarySourceHash;
        this.sourceAsOf = sourceAsOf;
        this.referenceVersion = referenceVersion;
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }
}

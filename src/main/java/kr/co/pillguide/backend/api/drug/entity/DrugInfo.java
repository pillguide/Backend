package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long infoId;

    @OneToOne
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(columnDefinition = "text")
    private String dosageMethod; //복용방법

    @Column(columnDefinition = "text")
    private String storageMethod; //보관방법

    @Column(columnDefinition = "text")
    private String sideEffects; //부작용

    @Column(columnDefinition = "text")
    private String effect;

    @Column(columnDefinition = "text")
    private String selectedSourcesJson;
    private String provenanceStatus;
    private LocalDateTime sourceFetchedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DrugInfo(Drug drug, String dosageMethod, String storageMethod,
                    String sideEffects, String effect) {
        this.drug = drug;
        this.dosageMethod = dosageMethod;
        this.storageMethod = storageMethod;
        this.sideEffects = sideEffects;
        this.effect = effect;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSelectedSources(String json, String provenanceStatus, LocalDateTime sourceFetchedAt) {
        if (json == null || json.isBlank() || provenanceStatus == null || provenanceStatus.isBlank()
                || sourceFetchedAt == null) {
            throw new IllegalArgumentException("Selected sources, status and fetch time are required");
        }
        this.selectedSourcesJson = json;
        this.provenanceStatus = provenanceStatus;
        this.sourceFetchedAt = sourceFetchedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDescriptions(String effect, String dosageMethod, String storageMethod,
                                   String sideEffects) {
        this.effect = effect;
        this.dosageMethod = dosageMethod;
        this.storageMethod = storageMethod;
        this.sideEffects = sideEffects;
        this.updatedAt = LocalDateTime.now();
    }
}

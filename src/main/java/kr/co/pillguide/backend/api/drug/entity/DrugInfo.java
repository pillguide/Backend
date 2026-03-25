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

    @Lob
    private String dosageMethod; //복용방법

    @Lob
    private String storageMethod; //보관방법

    @Lob
    private String sideEffects; //부작용

    @Lob
    private String effect;

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
}

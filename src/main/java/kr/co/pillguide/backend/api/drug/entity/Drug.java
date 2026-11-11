package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long drugId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    private String imageUrl;

    // The existing code is the string item_seq identity. These fields describe
    // the selected official reference data without replacing that identity.
    private String modelName;
    private String productCode;
    private String classificationCode;
    private String classificationName;
    @Column(columnDefinition = "text")
    private String ingredientDisplay;
    private String authorizationStatus;
    private String infoStatus;
    private LocalDate sourceAsOf;
    @Column(length = 1024)
    private String representativeImageKey;
    @Column(length = 64)
    private String representativeImageHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "drug", cascade = CascadeType.ALL, orphanRemoval = true)
    private DrugInfo drugInfo;

    public Drug(String name, String code, String imageUrl) {
        this.name = name;
        this.code = code;
        this.imageUrl = imageUrl;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void setDrugInfo(DrugInfo drugInfo) {
        this.drugInfo = drugInfo;
    }

    public void updateReferenceData(String officialName, String modelName, String productCode,
                                    String classificationCode, String classificationName,
                                    String ingredientDisplay, String authorizationStatus,
                                    String infoStatus, LocalDate sourceAsOf) {
        if ((officialName != null && officialName.isBlank())
                || infoStatus == null || infoStatus.isBlank()
                || sourceAsOf == null) {
            throw new IllegalArgumentException("Information status and source date are required");
        }
        if (officialName != null) {
            this.name = officialName;
        }
        this.modelName = modelName;
        this.productCode = productCode;
        this.classificationCode = classificationCode;
        this.classificationName = classificationName;
        this.ingredientDisplay = ingredientDisplay;
        this.authorizationStatus = authorizationStatus;
        this.infoStatus = infoStatus;
        this.sourceAsOf = sourceAsOf;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRepresentativeImage(String key, String hash) {
        if ((key == null) != (hash == null)
                || (key != null && (key.isBlank() || !hash.matches("[0-9a-fA-F]{64}")))) {
            throw new IllegalArgumentException("Representative image key and SHA-256 hash must be supplied together");
        }
        this.representativeImageKey = key;
        this.representativeImageHash = hash;
        this.updatedAt = LocalDateTime.now();
    }
}

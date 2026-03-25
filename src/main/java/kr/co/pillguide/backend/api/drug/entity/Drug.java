package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}

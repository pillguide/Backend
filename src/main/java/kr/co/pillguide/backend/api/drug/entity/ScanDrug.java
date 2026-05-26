package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanDrug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private ScanSession scanSession;

    @ManyToOne(fetch = FetchType.LAZY)
    private Drug drug;

    public ScanDrug(ScanSession scanSession, Drug drug) {
        this.scanSession = scanSession;
        this.drug = drug;
    }
}
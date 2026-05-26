package kr.co.pillguide.backend.api.drug.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.api.member.entity.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    private LocalDateTime scannedAt;

    @OneToMany(mappedBy = "scanSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScanDrug> drugs = new ArrayList<>();

    public ScanSession(Member member) {
        this.member = member;
        this.scannedAt = LocalDateTime.now();
    }

    public void addDrug(ScanDrug drug) {
        drugs.add(drug);
    }
}
package kr.co.pillguide.backend.api.mydrug.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.member.entity.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_drug", uniqueConstraints =
        @UniqueConstraint(name = "uk_member_drug_member_drug", columnNames = {"member_id", "drug_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDrug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(nullable = false)
    private LocalDateTime firstRegisteredAt;

    public MemberDrug(Member member, Drug drug) {
        if (member == null || drug == null) {
            throw new IllegalArgumentException("Member and drug are required");
        }
        this.member = member;
        this.drug = drug;
        this.firstRegisteredAt = LocalDateTime.now();
    }
}

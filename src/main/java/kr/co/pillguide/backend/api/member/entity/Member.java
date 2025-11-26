package kr.co.pillguide.backend.api.member.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.common.entity.BaseTimeEntity;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "oauth_id"})   // 공급자 + 공급자 ID 복합 유니크 키
)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이메일
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // OAuth2 공급자
    @Column(nullable = false)
    private String provider;

    // OAuth2 공급자 아이디
    @Column(nullable = false, length = 100)
    private String oauthId;

    // 본명
    @Column(nullable = false, length = 50)
    private String name;

    // 성별 (MALE, FEMALE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Gender gender;

    // 생년월일
    @Column(nullable = true)
    private LocalDate birthDate;

    // 권한
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 리프레쉬 토큰
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    @Builder
    public Member(String email,
                  String provider,
                  String oauthId,
                  String name,
                  Gender gender,
                  LocalDate birthDate,
                  Role role) {
        this.email = email;
        this.provider = provider;
        this.oauthId = oauthId;
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.role = role;
    }

    public void updateAdditionalInfo(Gender gender, LocalDate birthDate) {
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    public static Member newSocialMember(String name, String email, String provider, String providerId) {

        return new Member(email, provider, providerId, name, null, null, Role.USER);
    }

    public void addRefreshToken(RefreshToken refreshToken) {
        this.refreshTokens.add(refreshToken);
    }

    public void removeRefreshToken(RefreshToken refreshToken) {
        this.refreshTokens.remove(refreshToken);
    }

    public void clearAllRefreshTokens() {
        this.refreshTokens.clear();
    }
}

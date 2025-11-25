package kr.co.pillguide.backend.api.member.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.common.entity.BaseTimeEntity;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이메일
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // OAuth2 공급자
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    // OAuth2 공급자 유니크 키
    @Column(nullable = false, unique = true, length = 100)
    private String oauthId;

    // 본명
    @Column(nullable = false, length = 10)
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
    @Column(nullable = true, length = 2000)
    private String refreshToken;

    @Builder
    public Member(String email,
                  OAuthProvider provider,
                  String oauthId,
                  String name,
                  Gender gender,
                  LocalDate birthDate,
                  Role role,
                  String refreshToken) {
        this.email = email;
        this.provider = provider;
        this.oauthId = oauthId;
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.role = role;
        this.refreshToken = refreshToken;
    }

    public void updateAdditionalInfo(Gender gender, LocalDate birthDate) {
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

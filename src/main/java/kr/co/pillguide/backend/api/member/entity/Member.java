package kr.co.pillguide.backend.api.member.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.common.entity.BaseTimeEntity;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"oauthId", "provider"}) // 같은 소셜의 같은 ID는 중복 불가
})
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(nullable = false)
    private String oauthId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String gender;

    @Column(nullable = true)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = true, length = 2000)
    private String accessToken;

    @Column(nullable = true, length = 2000)
    private String refreshToken;

    @Builder
    public Member(String email,
                  OAuthProvider provider,
                  String oauthId,
                  String name,
                  String gender,
                  LocalDate birthDate,
                  Role role,
                  String accessToken,
                  String refreshToken) {
        this.email = email;
        this.provider = provider;
        this.oauthId = oauthId;
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.role = role;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public void updateAdditionalInfo(String gender, LocalDate birthDate) {
        this.gender = gender;
        this.birthDate = birthDate;
    }
}

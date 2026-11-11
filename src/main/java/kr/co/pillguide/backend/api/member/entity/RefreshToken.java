package kr.co.pillguide.backend.api.member.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Table(name = "refresh_token", uniqueConstraints =
        @UniqueConstraint(name = "uk_refresh_token_member", columnNames = "member_id"))
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public RefreshToken(String token, Member member, LocalDateTime expiresAt) {
        this.token = token;
        this.member = member;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public static RefreshToken of(String token, Member member, int expirationDays) {
        return RefreshToken.builder()
                .token(token)
                .member(member)
                .expiresAt(LocalDateTime.now().plusDays(expirationDays))
                .build();
    }

    public void updateToken(String newToken, LocalDateTime newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
    }
}

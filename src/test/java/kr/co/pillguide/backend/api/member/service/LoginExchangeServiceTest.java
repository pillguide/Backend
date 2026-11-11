package kr.co.pillguide.backend.api.member.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.entity.LoginExchange;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.LoginExchangeRepository;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LoginExchangeServiceTest {
    private final LoginExchangeRepository exchanges = mock(LoginExchangeRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final LoginExchangeService service = new LoginExchangeService(exchanges, authService, em);
    private final Member member = mock(Member.class);

    @Test
    void issuedCodeStoresOnlyHashAndExpiresSoon() {
        when(em.getReference(Member.class, 42L)).thenReturn(member);
        String challenge = "A".repeat(43);
        String code = service.issueCode(42L, challenge);
        ArgumentCaptor<LoginExchange> saved = ArgumentCaptor.forClass(LoginExchange.class);
        verify(exchanges).save(saved.capture());
        assertThat(code).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getCodeHash()).isEqualTo(hexSha256(code));
        assertThat(saved.getValue().getCodeHash()).doesNotContain(code);
        assertThat(saved.getValue().getChallenge()).isEqualTo(challenge);
        assertThat(saved.getValue().getMember()).isSameAs(member);
        assertThat(saved.getValue().isExpired(LocalDateTime.now().plusMinutes(3))).isTrue();
    }

    @Test
    void correctVerifierConsumesCodeAndIssuesTokens() {
        String code = "C".repeat(43);
        String verifier = "V".repeat(43);
        LoginExchange row = row(verifier, false);
        arrange(code, row);
        TokenResponseDTO tokens = new TokenResponseDTO("access", "refresh");
        when(authService.loginSuccess(member)).thenReturn(tokens);

        assertThat(service.exchange(code, verifier)).isSameAs(tokens);
        verify(em).find(Member.class, 42L, LockModeType.PESSIMISTIC_WRITE);
        verify(em).refresh(member, LockModeType.PESSIMISTIC_WRITE);
        verify(exchanges).delete(row);
        verify(authService).loginSuccess(member);
    }

    @Test
    void wrongVerifierDoesNotConsumeOrIssueTokens() {
        String code = "C".repeat(43);
        arrange(code, row("V".repeat(43), false));

        assertThatThrownBy(() -> service.exchange(code, "W".repeat(43)))
                .isInstanceOf(UnauthorizedException.class);
        verify(exchanges, never()).delete(any());
        verifyNoInteractions(authService);
    }

    @Test
    void expiredOrAlreadyUsedCodeCannotIssueTokens() {
        String code = "C".repeat(43);
        arrange(code, row("V".repeat(43), true));
        assertThatThrownBy(() -> service.exchange(code, "V".repeat(43)))
                .isInstanceOf(UnauthorizedException.class);
        verify(exchanges, never()).delete(any());
        verifyNoInteractions(authService);

        reset(exchanges);
        assertThatThrownBy(() -> service.exchange(code, "V".repeat(43)))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(authService);
    }

    private LoginExchange row(String verifier, boolean expired) {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return new LoginExchange("hash", challenge, member,
                LocalDateTime.now().plusMinutes(expired ? -1 : 1));
    }

    private void arrange(String code, LoginExchange row) {
        when(exchanges.findMemberIdByCodeHash(hexSha256(code))).thenReturn(Optional.of(42L));
        when(em.find(Member.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(member);
        when(exchanges.findForUpdateByCodeHash(hexSha256(code))).thenReturn(Optional.of(row));
    }

    private static String hexSha256(String value) {
        return java.util.HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

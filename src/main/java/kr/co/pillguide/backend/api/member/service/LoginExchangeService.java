package kr.co.pillguide.backend.api.member.service;

import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.api.member.dto.TokenResponseDTO;
import kr.co.pillguide.backend.api.member.entity.LoginExchange;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.LoginExchangeRepository;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LoginExchangeService {
    private static final Pattern BASE64URL_32_BYTES = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_TTL_MINUTES = 2;

    private final LoginExchangeRepository exchanges;
    private final AuthService authService;
    private final EntityManager em;

    @Transactional
    public String issueCode(Long memberId, String challenge) {
        if (memberId == null || !isChallenge(challenge)) {
            throw new UnauthorizedException("로그인을 다시 시도해 주세요.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Member member = em.getReference(Member.class, memberId);
        exchanges.save(new LoginExchange(hash(code), challenge, member,
                LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES)));
        return code;
    }

    @Transactional
    public TokenResponseDTO exchange(String code, String verifier) {
        if (!isChallenge(code) || !isChallenge(verifier)) {
            throw invalidCode();
        }
        String codeHash = hash(code);
        Long memberId = exchanges.findMemberIdByCodeHash(codeHash).orElseThrow(this::invalidCode);

        // All auth mutations acquire the member lock before any exchange/token row lock.
        Member member;
        try {
            member = MemberLocks.lock(em, memberId);
        } catch (NotFoundException e) {
            throw invalidCode();
        }
        LoginExchange exchange = exchanges.findForUpdateByCodeHash(codeHash).orElseThrow(this::invalidCode);
        if (exchange.isExpired(LocalDateTime.now())) {
            throw invalidCode();
        }
        String actualChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        if (!MessageDigest.isEqual(exchange.getChallenge().getBytes(StandardCharsets.US_ASCII),
                actualChallenge.getBytes(StandardCharsets.US_ASCII))) {
            throw invalidCode();
        }

        exchanges.delete(exchange);
        // Deletion and token issuance must commit or roll back as one transaction.
        return authService.loginSuccess(member);
    }

    public static boolean isChallenge(String value) {
        return value != null && BASE64URL_32_BYTES.matcher(value).matches();
    }

    private UnauthorizedException invalidCode() {
        return new UnauthorizedException("로그인 교환 코드가 유효하지 않습니다.");
    }

    private static String hash(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

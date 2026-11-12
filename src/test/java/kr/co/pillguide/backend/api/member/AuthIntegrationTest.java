package kr.co.pillguide.backend.api.member;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.repository.RefreshTokenRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MemberRepository members;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @MockitoBean FcmService fcm;
    @Value("${jwt.secretKey}") String secret;

    Long memberId;

    @BeforeEach
    void setUp() {
        Member member = members.saveAndFlush(Member.newSocialMember(
                "auth-test", "auth@example.invalid", "test", UUID.randomUUID().toString()));
        memberId = member.getId();
    }

    MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build(); }

    @Test
    void everyRegisteredBusinessMappingRejectsAnonymousAndRefreshBearer() throws Exception {
        String refresh = auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
        MockMvc client = mvc();
        int checked = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("kr.co.pillguide.backend.api.")) continue;
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            if (methods.isEmpty()) methods = Set.of(RequestMethod.GET);
            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/v1/") || Set.of("/api/v1/auth/reissue", "/api/v1/auth/logout",
                        "/api/v1/auth/exchange").contains(pattern)) continue;
                String path = pattern.replaceAll("\\{[^}]+\\}", "1");
                for (RequestMethod method : methods) {
                    var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .request(org.springframework.http.HttpMethod.valueOf(method.name()), path);
                    client.perform(request).andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.status").value(401));
                    var refreshRequest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .request(org.springframework.http.HttpMethod.valueOf(method.name()), path)
                            .header("Authorization", "Bearer " + refresh);
                    client.perform(refreshRequest).andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.status").value(401));
                    checked++;
                }
            }
        }
        assertTrue(checked >= 10, "Expected an inventory of registered business mappings");
    }

    @Test
    void signedAccessWorksWithoutAdditionalProfileAndLogoutRevokesIt() throws Exception {
        var first = auth.loginSuccess(members.findById(memberId).orElseThrow());
        mvc().perform(get("/api/v1/medication/reminders").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk());
        var second = auth.loginSuccess(members.findById(memberId).orElseThrow());
        assertNotEquals(first.refreshToken(), second.refreshToken());
        mvc().perform(get("/api/v1/medication/reminders").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk());
        mvc().perform(post("/api/v1/auth/logout").header("X-Refresh-Token", second.refreshToken()))
                .andExpect(status().isOk());
        mvc().perform(get("/api/v1/medication/reminders").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc().perform(post("/api/v1/auth/reissue").header("X-Refresh-Token", second.refreshToken()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void malformedExpiredAndWrongPurposeAccessReturn401() throws Exception {
        var first = auth.loginSuccess(members.findById(memberId).orElseThrow());
        String access = first.accessToken();
        int signature = access.lastIndexOf('.') + 1;
        String tampered = access.substring(0, signature)
                + (access.charAt(signature) == 'A' ? 'B' : 'A') + access.substring(signature + 1);
        for (String token : new String[] { tampered, first.refreshToken(), expiredAccess(),
                signedAccess(memberId.toString(), null, null, null), // Legacy JWT without new claims.
                signedAccess(memberId.toString(), "access", 0, null),
                signedAccess("not-a-member-id", "access", 0, UUID.randomUUID().toString()),
                signedAccess(memberId.toString(), "access", -1, UUID.randomUUID().toString()) }) {
            mvc().perform(get("/api/v1/medication/reminders").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void tokenForDeletedMemberReturns401() throws Exception {
        String access = auth.loginSuccess(members.findById(memberId).orElseThrow()).accessToken();
        members.deleteById(memberId);
        mvc().perform(get("/api/v1/medication/reminders").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refreshHeaderIsRequiredAndRotationRejectsPreviousToken() throws Exception {
        mvc().perform(post("/api/v1/auth/reissue"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc().perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        String original = auth.loginSuccess(members.findById(memberId).orElseThrow()).refreshToken();
        String rotated = auth.reissue(original).refreshToken();
        assertNotEquals(original, rotated);
        mvc().perform(post("/api/v1/auth/reissue").header("X-Refresh-Token", original))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc().perform(post("/api/v1/auth/logout").header("X-Refresh-Token", original))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        assertEquals(rotated, refreshTokens.findByMemberId(memberId).orElseThrow().getToken());
    }

    @Test
    void publicDocumentationAndProbeAreReachable() throws Exception {
        mvc().perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc().perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc().perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    private String expiredAccess() {
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject(memberId.toString()).setId(UUID.randomUUID().toString())
                .claim("purpose", "access").claim("authVersion", 0)
                .setIssuedAt(new Date(now - 120_000)).setExpiration(new Date(now - 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private String signedAccess(String subject, String purpose, Integer version, String id) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder().setSubject(subject)
                .setIssuedAt(new Date(now)).setExpiration(new Date(now + 60_000));
        if (id != null) builder.setId(id);
        if (purpose != null) builder.claim("purpose", purpose);
        if (version != null) builder.claim("authVersion", version);
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256).compact();
    }
}

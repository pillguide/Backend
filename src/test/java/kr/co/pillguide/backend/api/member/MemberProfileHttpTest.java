package kr.co.pillguide.backend.api.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "reference.sync.enabled=false")
class MemberProfileHttpTest {
    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired AuthService auth;
    @MockitoBean FcmService fcm;

    MockMvc mvc;
    Member member;
    Member other;
    String access;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        member = members.saveAndFlush(Member.newSocialMember("Old name", null,
                "google", UUID.randomUUID().toString()));
        other = members.saveAndFlush(Member.newSocialMember("Other member", "other@example.com",
                "kakao", UUID.randomUUID().toString()));
        access = auth.loginSuccess(member).accessToken();
    }

    @Test
    void readsOnlyCurrentProfileWithNullOptionalFields() throws Exception {
        JsonNode data = mapper.readTree(mvc.perform(get("/api/v1/member/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals("Old name", data.path("name").asText());
        assertTrue(data.path("gender").isNull());
        assertTrue(data.path("birthDate").isNull());
        assertTrue(data.path("email").isNull());
        assertFalse(data.has("provider"));
        assertFalse(data.has("oauthId"));
    }

    @Test
    void updatesSelectedFieldsOnlyAndDoesNotAcceptIdentityChanges() throws Exception {
        String originalOAuthId = member.getOauthId();
        JsonNode data = mapper.readTree(mvc.perform(put("/api/v1/member/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  New name  \",\"gender\":\"FEMALE\",\"birthDate\":null,"
                                + "\"provider\":\"attacker\",\"oauthId\":\"attacker\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals("New name", data.path("name").asText());
        assertEquals("FEMALE", data.path("gender").asText());
        assertTrue(data.path("birthDate").isNull());
        Member stored = members.findById(member.getId()).orElseThrow();
        assertEquals("google", stored.getProvider());
        assertEquals(originalOAuthId, stored.getOauthId());
        assertEquals("Other member", members.findById(other.getId()).orElseThrow().getName());
    }

    @Test
    void rejectsInvalidFieldsWithoutChangingProfile() throws Exception {
        for (String body : new String[] {
                "{}", "{\"name\":null}", "{\"name\":\"   \"}",
                "{\"email\":\"invalid\"}", "{\"gender\":\"OTHER\"}",
                "{\"birthDate\":\"2026-02-30\"}", "{\"birthDate\":\"2999-01-01\"}"}) {
            mvc.perform(put("/api/v1/member/me")
                    .header("Authorization", "Bearer " + access)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        Member stored = members.findById(member.getId()).orElseThrow();
        assertEquals("Old name", stored.getName());
        assertNull(stored.getEmail());
        assertNull(stored.getGender());
    }

    @Test
    void updatesBirthDateAndEmailAndReadsThemBack() throws Exception {
        mvc.perform(put("/api/v1/member/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthDate\":\"2000-01-02\",\"email\":\"member@example.com\"}"))
                .andExpect(status().isOk());
        JsonNode data = mapper.readTree(mvc.perform(get("/api/v1/member/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals("Old name", data.path("name").asText());
        assertEquals("2000-01-02", data.path("birthDate").asText());
        assertEquals("member@example.com", data.path("email").asText());
    }

    @Test
    void requiresCurrentAuthentication() throws Exception {
        mvc.perform(get("/api/v1/member/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/member/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Changed\"}"))
                .andExpect(status().isUnauthorized());
        member.revokeAuthentication();
        members.saveAndFlush(member);
        mvc.perform(get("/api/v1/member/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/member/me").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
                .andExpect(status().isUnauthorized());
    }
}

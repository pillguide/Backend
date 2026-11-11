package kr.co.pillguide.backend.api.mydrug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.ScanDrug;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.api.drug.repository.ScanSessionRepository;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.mydrug.repository.MemberDrugRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP, security and envelope contract; PostgreSQL concurrency is checked separately. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "reference.sync.enabled=false")
class MyDrugHttpTest {
    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired DrugRepository drugs;
    @Autowired ScanSessionRepository sessions;
    @Autowired MemberDrugRepository relations;
    @Autowired AuthService auth;
    @MockitoBean FcmService fcm;

    private MockMvc mvc;
    private Member member;
    private Member other;
    private Drug a;
    private Drug b;
    private ScanSession scan;
    private ScanSession foreignScan;
    private String access;
    private String refresh;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        member = members.saveAndFlush(Member.newSocialMember("my-drug-http", null,
                "test", UUID.randomUUID().toString()));
        other = members.saveAndFlush(Member.newSocialMember("other-my-drug", null,
                "test", UUID.randomUUID().toString()));
        a = drugs.saveAndFlush(new Drug("Original A", "test-" + UUID.randomUUID(), null));
        b = drugs.saveAndFlush(new Drug("Original B", "test-" + UUID.randomUUID(), null));
        a.updateReferenceData("Current A", "Model A", "K-000001", "100", "Category", "Ingredient",
                "NORMAL", "COMPLETE", LocalDate.of(2026, 11, 11));
        a = drugs.saveAndFlush(a);
        scan = new ScanSession(member); // Existing JSON-era session remains selectable by detection ID.
        scan.addDrug(new ScanDrug(scan, a));
        scan.addDrug(new ScanDrug(scan, a));
        scan.addDrug(new ScanDrug(scan, b));
        scan = sessions.saveAndFlush(scan);
        foreignScan = new ScanSession(other);
        foreignScan.addDrug(new ScanDrug(foreignScan, b));
        foreignScan = sessions.saveAndFlush(foreignScan);
        var tokens = auth.loginSuccess(member);
        access = tokens.accessToken();
        refresh = tokens.refreshToken();
    }

    @Test
    void duplicateDetectionsCreateOneRelationAndReplayPreservesFirstRegistration() throws Exception {
        Long first = scan.getDrugs().get(0).getId();
        Long duplicate = scan.getDrugs().get(1).getId();
        JsonNode created = body(mvc.perform(post("/api/v1/my-drugs")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection(scan.getId(), List.of(first, duplicate))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201)).andReturn());
        JsonNode selected = created.path("data");
        assertEquals(1, selected.size());
        long relationId = selected.get(0).path("memberDrugId").asLong();
        String firstDate = selected.get(0).path("firstRegisteredAt").asText();
        assertEquals(a.getDrugId().longValue(), selected.get(0).path("drugId").asLong());
        assertEquals("Current A", selected.get(0).path("name").asText());
        assertEquals("COMPLETE", selected.get(0).path("infoStatus").asText());
        assertEquals("2026-11-11", selected.get(0).path("sourceAsOf").asText());
        assertEquals("NOT_READY", selected.get(0).path("representativeImageStatus").asText());
        assertEquals(1, relations.findAllByMember_IdOrderByFirstRegisteredAtDescIdDesc(
                member.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements());

        JsonNode repeated = body(mvc.perform(post("/api/v1/my-drugs")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection(scan.getId(), List.of(duplicate))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200)).andReturn()).path("data");
        assertEquals(relationId, repeated.get(0).path("memberDrugId").asLong());
        assertEquals(firstDate, repeated.get(0).path("firstRegisteredAt").asText());

        JsonNode list = body(mvc.perform(get("/api/v1/my-drugs?page=0&size=1")
                        .header("Authorization", bearer(access)))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertEquals(1, list.path("totalElements").asInt());
        assertEquals(1, list.path("items").size());
        assertEquals(relationId, list.path("items").get(0).path("memberDrugId").asLong());
        assertEquals("Current A", list.path("items").get(0).path("name").asText());
        assertEquals("NOT_READY", list.path("items").get(0).path("representativeImageStatus").asText());

        JsonNode detail = body(mvc.perform(get("/api/v1/my-drugs/{id}", relationId)
                        .header("Authorization", bearer(access)))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertEquals(firstDate, detail.path("firstRegisteredAt").asText());
        assertEquals("Current A", detail.path("drug").path("name").asText());
        assertEquals("COMPLETE", detail.path("drug").path("reference").path("infoStatus").asText());

        String otherAccess = auth.loginSuccess(other).accessToken();
        mvc.perform(get("/api/v1/my-drugs/{id}", relationId)
                        .header("Authorization", bearer(otherAccess)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/my-drugs/{id}", relationId)
                        .header("Authorization", bearer(otherAccess)))
                .andExpect(status().isNotFound());

        body(mvc.perform(delete("/api/v1/my-drugs/{id}", relationId)
                        .header("Authorization", bearer(access)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(relationId)).andReturn());
        mvc.perform(get("/api/v1/my-drugs/{id}", relationId).header("Authorization", bearer(access)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scan/{id}", scan.getId()).header("Authorization", bearer(access)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.drugCount").value(3));
        assertTrue(sessions.findById(scan.getId()).isPresent());
        assertTrue(drugs.findById(a.getDrugId()).isPresent());
    }

    @Test
    void invalidAndForeignSelectionsAreAtomicAndAuthenticationIsRequired() throws Exception {
        Long own = scan.getDrugs().get(0).getId();
        Long foreign = foreignScan.getDrugs().get(0).getId();
        List<String> badBodies = new ArrayList<>(List.of(
                selection(scan.getId(), List.of()),
                selection(-1L, List.of(own)),
                selection(scan.getId(), List.of(-1L)),
                selection(scan.getId(), java.util.Collections.nCopies(301, own))));
        badBodies.add("{}");
        for (String json : badBodies) {
            mvc.perform(post("/api/v1/my-drugs").header("Authorization", bearer(access))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        for (String json : List.of(selection(scan.getId(), List.of(own, Long.MAX_VALUE)),
                selection(scan.getId(), List.of(own, foreign)),
                selection(foreignScan.getId(), List.of(foreign)))) {
            mvc.perform(post("/api/v1/my-drugs").header("Authorization", bearer(access))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
        assertEquals(0, relations.findAllByMember_IdOrderByFirstRegisteredAtDescIdDesc(
                member.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements());
        String valid = selection(scan.getId(), List.of(own));
        mvc.perform(post("/api/v1/my-drugs").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/api/v1/my-drugs").header("Authorization", bearer(refresh))
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void documentationPublishesMyDrugRoutesAndStatuses() throws Exception {
        JsonNode paths = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()).path("paths");
        JsonNode collection = paths.path("/api/v1/my-drugs");
        JsonNode item = paths.path("/api/v1/my-drugs/{memberDrugId}");
        for (String code : List.of("201", "200", "400", "401", "404", "500"))
            assertTrue(collection.path("post").path("responses").has(code), "POST " + code);
        for (String code : List.of("200", "400", "401"))
            assertTrue(collection.path("get").path("responses").has(code), "GET list " + code);
        for (String code : List.of("200", "401", "404")) {
            assertTrue(item.path("get").path("responses").has(code), "GET detail " + code);
            assertTrue(item.path("delete").path("responses").has(code), "DELETE " + code);
        }
    }

    private String selection(Long sessionId, List<Long> ids) throws Exception {
        return mapper.writeValueAsString(Map.of("scanSessionId", sessionId, "detectionIds", ids));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String value) { return "Bearer " + value; }
}

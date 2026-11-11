package kr.co.pillguide.backend.api.scan.service;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.reference.ReferenceReadService;
import kr.co.pillguide.backend.api.drug.reference.ReferenceSnapshot;
import kr.co.pillguide.backend.api.drug.reference.SupportedDrugCatalog;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.api.drug.service.ScanService;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.Role;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.scan.exception.ScanException;
import kr.co.pillguide.backend.api.scan.inference.AiClient;
import kr.co.pillguide.backend.api.scan.inference.AiPredictionResult;
import kr.co.pillguide.backend.api.scan.inference.PhotoInput;
import kr.co.pillguide.backend.api.scan.inference.PhotoValidator;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class ScanWorkflowPostgresTest {
    private static final String A = "197500541";
    private static final String B = "200001783";
    private static final String MODEL_HASH = "a".repeat(64);

    @Autowired ScanWorkflow workflow;
    @Autowired ScanService scans;
    @Autowired SupportedDrugCatalog catalog;
    @Autowired MemberRepository members;
    @Autowired DrugRepository drugs;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PhotoValidator photos;
    @MockitoBean AiClient ai;
    @MockitoBean ReferenceReadService references;
    @MockitoBean FcmService fcm;

    private Member member;
    private Drug drugA;
    private Drug drugB;
    private JwtService.TokenClaims claims;

    @BeforeEach
    void setup() throws Exception {
        member = members.saveAndFlush(Member.builder().provider("test")
                .oauthId(UUID.randomUUID().toString()).name("Scan tester").role(Role.ROLE_USER).build());
        drugA = drug(A, "Drug A");
        drugB = drug(B, "Drug B");
        claims = new JwtService.TokenClaims(member.getId(), member.getAuthVersion(), Instant.now().plusSeconds(3600));
        when(photos.validate(any())).thenAnswer(call -> {
            byte[] bytes = call.<MockMultipartFile>getArgument(0).getBytes();
            return new PhotoInput(bytes, "image/png", sha(bytes), 100, 100);
        });
        when(references.read(anySet())).thenAnswer(call -> snapshot(call.getArgument(0)));
    }

    @Test
    void repeatedDetectionIsThreeCardsTwoUniqueProductsAndOneCombination() {
        when(ai.predict(any())).thenReturn(predictions(A, A, B));
        var outcome = workflow.analyze(claims, UUID.randomUUID(), file("photo-one"));
        assertTrue(outcome.created());
        assertEquals(3, outcome.result().drugCount());
        assertEquals(3, outcome.result().drugs().size());
        assertNotEquals(outcome.result().drugs().get(0).detectionId(),
                outcome.result().drugs().get(1).detectionId());
        assertEquals(2, outcome.result().combination().uniqueDrugCount());
        assertEquals(1, outcome.result().combination().pairCount());
        assertEquals(1, outcome.result().combination().confirmedContraindicationCount());
        assertNull(outcome.result().drugs().get(0).imageUrl());
        assertEquals("NOT_READY", outcome.result().drugs().get(0).representativeImageStatus());
        assertEquals(3L, jdbc.queryForObject("select count(*) from scan_drug where scan_session_id = ?",
                Long.class, outcome.result().sessionId()));
    }

    @Test
    void emptyPredictionStillCreatesOneHistorySession() {
        when(ai.predict(any())).thenReturn(predictions());
        var outcome = workflow.analyze(claims, UUID.randomUUID(), file("empty-photo"));
        assertTrue(outcome.created());
        assertEquals(0, outcome.result().drugCount());
        assertEquals(0, outcome.result().combination().pairCount());
        assertEquals(1L, countSessions(member.getId()));
        assertEquals(0L, jdbc.queryForObject("select count(*) from scan_drug where scan_session_id = ?",
                Long.class, outcome.result().sessionId()));
    }

    @Test
    void failedInferenceCreatesNoSuccessHistory() {
        when(ai.predict(any())).thenThrow(new ScanException(HttpStatus.BAD_GATEWAY, "분석할 수 없습니다.", null));
        assertThrows(ScanException.class, () -> workflow.analyze(claims, UUID.randomUUID(), file("bad-photo")));
        assertEquals(0L, countSessions(member.getId()));
        verifyNoInteractions(references);
    }

    @Test
    void failedChildForeignKeyRollsBackTheAlreadyInsertedSession() {
        when(ai.predict(any())).thenReturn(predictions(A));
        when(references.read(anySet())).thenAnswer(call -> {
            ReferenceSnapshot normal = snapshot(call.getArgument(0));
            return new ReferenceSnapshot(normal.version(), normal.sourceAsOf(), normal.refreshDelayed(),
                    Map.of(A, new ReferenceSnapshot.ReferencedDrug(-1L, A, "Drug A", "COMPLETE", "NORMAL")),
                    normal.combination());
        });
        assertThrows(RuntimeException.class,
                () -> workflow.analyze(claims, UUID.randomUUID(), file("missing-fk-photo")));
        assertEquals(0L, countSessions(member.getId()));
        assertEquals(0L, jdbc.queryForObject("select count(*) from scan_drug d join scan_session s "
                + "on s.id = d.scan_session_id where s.member_id = ?", Long.class, member.getId()));
    }

    @Test
    void concurrentIdenticalRequestWritesOnceAndReplaysTheSameResult() throws Exception {
        CyclicBarrier aiBarrier = new CyclicBarrier(2);
        when(ai.predict(any())).thenAnswer(call -> {
            aiBarrier.await(10, TimeUnit.SECONDS);
            return predictions(A, B);
        });
        UUID requestId = UUID.randomUUID();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> workflow.analyze(claims, requestId, file("same-photo")));
            var second = workers.submit(() -> workflow.analyze(claims, requestId, file("same-photo")));
            var one = first.get(20, TimeUnit.SECONDS);
            var two = second.get(20, TimeUnit.SECONDS);
            assertEquals(one.result().sessionId(), two.result().sessionId());
            assertEquals(1, (one.created() ? 1 : 0) + (two.created() ? 1 : 0));
        }
        assertEquals(1L, countSessions(member.getId()));
    }

    @Test
    void concurrentDifferentPhotosWithSameRequestIdConflict() throws Exception {
        CyclicBarrier aiBarrier = new CyclicBarrier(2);
        when(ai.predict(any())).thenAnswer(call -> {
            aiBarrier.await(10, TimeUnit.SECONDS);
            return predictions(A);
        });
        UUID requestId = UUID.randomUUID();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> outcomeOrError(requestId, file("first-photo")));
            var second = workers.submit(() -> outcomeOrError(requestId, file("second-photo")));
            Object one = first.get(20, TimeUnit.SECONDS);
            Object two = second.get(20, TimeUnit.SECONDS);
            assertEquals(1, (one instanceof ScanWorkflow.Outcome ? 1 : 0)
                    + (two instanceof ScanWorkflow.Outcome ? 1 : 0));
            Object error = one instanceof ScanException ? one : two;
            assertInstanceOf(ScanException.class, error);
            assertEquals(HttpStatus.CONFLICT, ((ScanException) error).status());
        }
        assertEquals(1L, countSessions(member.getId()));
    }

    @Test
    void logoutCommittedWhileAiRunsRejectsWriteAndLeavesNoHistory() throws Exception {
        var tokens = auth.loginSuccess(member);
        JwtService.TokenClaims active = new JwtService.TokenClaims(member.getId(), member.getAuthVersion(),
                Instant.now().plusSeconds(3600));
        CountDownLatch enteredAi = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        when(ai.predict(any())).thenAnswer(call -> {
            enteredAi.countDown();
            assertTrue(releaseAi.await(10, TimeUnit.SECONDS));
            return predictions(A);
        });
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> outcomeOrError(active, UUID.randomUUID(), file("logout-photo")));
            assertTrue(enteredAi.await(5, TimeUnit.SECONDS));
            auth.logout(tokens.refreshToken());
            releaseAi.countDown();
            assertInstanceOf(UnauthorizedException.class, result.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0L, countSessions(member.getId()));
    }

    @Test
    void memberDeletedWhileAiRunsReturnsUnauthorizedAndLeavesNoHistory() throws Exception {
        CountDownLatch enteredAi = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        when(ai.predict(any())).thenAnswer(call -> {
            enteredAi.countDown();
            assertTrue(releaseAi.await(10, TimeUnit.SECONDS));
            return predictions(A);
        });
        Long deletedId = member.getId();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> outcomeOrError(claims, UUID.randomUUID(), file("deleted-member")));
            assertTrue(enteredAi.await(5, TimeUnit.SECONDS));
            members.deleteById(deletedId);
            members.flush();
            releaseAi.countDown();
            assertInstanceOf(UnauthorizedException.class, result.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0L, countSessions(deletedId));
    }

    @Test
    void historicalDetectionAndCombinationRemainAfterReferenceNameChanges() {
        when(ai.predict(any())).thenReturn(predictions(A, B));
        var result = workflow.analyze(claims, UUID.randomUUID(), file("history-photo")).result();
        jdbc.update("update drug set name = 'Renamed later' where drug_id = ?", drugA.getDrugId());
        var history = scans.getScanSession(member.getId(), result.sessionId());
        assertEquals("Drug A", history.drugs().get(0).name());
        assertEquals("Drug A", history.combination().matches().getFirst().firstName());
        assertEquals(result.referenceVersion(), history.referenceVersion());
        assertEquals(result.combination(), history.combination());
        var currentDetail = scans.getScanDrug(member.getId(), result.sessionId(), drugA.getDrugId());
        assertEquals("Renamed later", currentDetail.name());
        assertNull(currentDetail.imageUrl());
        assertEquals("NOT_READY", currentDetail.representativeImageStatus());
    }

    @Test
    void anotherMemberCannotReadSession() {
        when(ai.predict(any())).thenReturn(predictions(A));
        var result = workflow.analyze(claims, UUID.randomUUID(), file("private-photo")).result();
        Member other = members.saveAndFlush(Member.builder().provider("test")
                .oauthId(UUID.randomUUID().toString()).name("Other").role(Role.ROLE_USER).build());
        assertThrows(NotFoundException.class, () -> scans.getScanSession(other.getId(), result.sessionId()));
        assertThrows(NotFoundException.class, () -> scans.getScanDrug(other.getId(), result.sessionId(), drugA.getDrugId()));
    }

    private Object outcomeOrError(UUID requestId, MockMultipartFile file) {
        return outcomeOrError(claims, requestId, file);
    }

    private Object outcomeOrError(JwtService.TokenClaims active, UUID requestId, MockMultipartFile file) {
        try { return workflow.analyze(active, requestId, file); }
        catch (RuntimeException error) { return error; }
    }

    private Drug drug(String code, String name) {
        Drug drug = drugs.findByCode(code).orElseGet(() -> drugs.saveAndFlush(new Drug(name, code, null)));
        jdbc.update("update drug set name = ? where drug_id = ?", name, drug.getDrugId());
        return drug;
    }

    private ReferenceSnapshot snapshot(Set<String> itemSeqs) {
        var selected = new java.util.HashMap<String, ReferenceSnapshot.ReferencedDrug>();
        if (itemSeqs.contains(A)) selected.put(A, new ReferenceSnapshot.ReferencedDrug(
                drugA.getDrugId(), A, "Drug A", "COMPLETE", "NORMAL"));
        if (itemSeqs.contains(B)) selected.put(B, new ReferenceSnapshot.ReferencedDrug(
                drugB.getDrugId(), B, "Drug B", "COMPLETE", "NORMAL"));
        List<ReferenceSnapshot.Match> matches = itemSeqs.containsAll(Set.of(A, B))
                ? List.of(new ReferenceSnapshot.Match(drugA.getDrugId(), drugB.getDrugId(), A, B,
                "Drug A", "Drug B", "DIRECT_ITEM", "고칼륨혈증", "고칼륨혈증이 생길 수 있습니다.",
                "b".repeat(64), LocalDate.of(2026, 11, 11), List.of(Map.of("kind", "DIRECT_ITEM"))))
                : List.of();
        int unique = selected.size();
        var combination = new ReferenceSnapshot.Combination(unique, unique * (unique - 1) / 2,
                matches.size(), matches, "REVIEWED_LIST_ONLY", LocalDate.of(2026, 11, 11), false);
        return new ReferenceSnapshot("ref-v1", LocalDate.of(2026, 11, 11), false, selected, combination);
    }

    private AiPredictionResult predictions(String... itemSeqs) {
        List<AiPredictionResult.Prediction> rows = new ArrayList<>();
        for (int i = 0; i < itemSeqs.length; i++) {
            String seq = itemSeqs[i];
            rows.add(new AiPredictionResult.Prediction(i + 1, catalog.byItemSeq(seq).orElseThrow().classId(),
                    seq, 0.9, List.of(0.1, 0.1, 0.4, 0.4), false));
        }
        return new AiPredictionResult(100, 100, MODEL_HASH, catalog.mappingSha256(), rows);
    }

    private MockMultipartFile file(String text) {
        return new MockMultipartFile("file", "pill.png", "image/png", text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long countSessions(Long memberId) {
        return jdbc.queryForObject("select count(*) from scan_session where member_id = ?", Long.class, memberId);
    }
}

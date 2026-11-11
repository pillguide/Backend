package kr.co.pillguide.backend.api.drug.reference;

import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.common.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

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
class ReferenceReadPostgresTest {
    private static final String HASH = "e".repeat(64);
    private static final Set<String> PAIR = Set.of("197500541", "200001783");
    @Autowired ReferenceReadService reader;
    @Autowired ReferencePublisher publisher;
    @Autowired ReferenceSyncService sync;
    @Autowired SupportedDrugCatalog catalog;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DrugRepository drugs;
    @MockitoBean FcmService fcm;

    @BeforeEach
    void resetState() { jdbc.update("delete from reference_sync_state"); }

    @Test
    void noInitialReferenceIsUnavailableEvenForEmptyDetection() {
        BaseException error = assertThrows(BaseException.class, () -> reader.read(Set.of()));
        assertEquals(503, error.getStatusCode());
    }

    @Test
    void comparesDistinctProductsAndPreservesNullEvidenceAndCoverageLimits() {
        publish("first", false);
        var result = reader.read(PAIR);
        assertEquals(2, result.combination().uniqueDrugCount());
        assertEquals(1, result.combination().pairCount());
        assertEquals(1, result.combination().confirmedContraindicationCount());
        assertEquals("REVIEWED_LIST_ONLY", result.combination().coverageStatus());
        var evidence = result.combination().matches().getFirst().evidence().getFirst();
        assertTrue(evidence.containsKey("reason"));
        assertNull(evidence.get("reason"));
        var empty = reader.read(Set.of());
        assertTrue(empty.drugs().isEmpty());
        assertEquals(0, empty.combination().pairCount());
        assertTrue(empty.combination().matches().isEmpty());
    }

    @Test
    void allFourReviewedPairsMatchInEitherOrderAndUnreviewedPairsStayUnconfirmed() {
        publish("rules", false);
        for (var approved : ApprovedInteractionPairs.ALL) {
            String first = approved.firstItemSeq();
            String second = approved.secondItemSeq();
            for (List<String> order : List.of(List.of(first, second), List.of(second, first))) {
                var snapshot = reader.read(new LinkedHashSet<>(order));
                assertEquals(2, snapshot.combination().uniqueDrugCount());
                assertEquals(1, snapshot.combination().pairCount());
                assertEquals(1, snapshot.combination().confirmedContraindicationCount());
                var match = snapshot.combination().matches().getFirst();
                assertEquals(Set.of(first, second), Set.of(match.firstItemSeq(), match.secondItemSeq()));
                assertEquals(approved.kind().name(), match.evidenceType());
                assertEquals("rules summary", match.summary());
            }
        }
        String unrelated = catalog.all().stream().map(SupportedDrugCatalog.SupportedDrug::itemSeq)
                .filter(seq -> ApprovedInteractionPairs.ALL.stream().noneMatch(pair ->
                        pair.firstItemSeq().equals(seq) || pair.secondItemSeq().equals(seq)))
                .findFirst().orElseThrow();
        var noMatch = reader.read(Set.of("197500541", unrelated));
        assertEquals(1, noMatch.combination().pairCount());
        assertEquals(0, noMatch.combination().confirmedContraindicationCount());
        assertTrue(noMatch.combination().matches().isEmpty());
        assertEquals("REVIEWED_LIST_ONLY", noMatch.combination().coverageStatus());
    }

    @Test
    void staleAndMissingOfficialDataAreReportedWithoutInventingSafety() {
        publish("partial", true);
        UUID failed = sync.begin(false).orElseThrow();
        sync.fail(failed, "HTTP_TIMEOUT");
        var result = reader.read(PAIR);
        assertTrue(result.refreshDelayed());
        assertEquals("REFERENCE_DATA_PARTIAL", result.combination().coverageStatus());
        assertEquals("NO_OFFICIAL_RECORD", result.drugs().get("197500541").infoStatus());
        assertEquals(1, result.combination().matches().size());
    }

    @Test
    void corruptRuleGenerationNeverBecomesASuccessfulNoMatch() {
        publish("first", false);
        jdbc.update("update drug_interaction set reference_version = 'other-version'");
        assertThrows(IllegalStateException.class, () -> reader.read(PAIR));
    }

    @Test
    void concurrentPublicationCannotMixVersionNameAndInteractionSnapshot() throws Exception {
        String oldVersion = publish("old", false);
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch published = new CountDownLatch(1);
        AtomicBoolean pauseOnce = new AtomicBoolean(true);
        var repositoryDelegate = mockingDetails(drugs).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            if (pauseOnce.compareAndSet(true, false)) {
                firstRead.countDown();
                assertTrue(published.await(15, TimeUnit.SECONDS));
            }
            return repositoryDelegate.answer(invocation);
        }).when(drugs).findAllByCodeIn(any());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var reading = pool.submit(() -> reader.read(PAIR));
            try {
                assertTrue(firstRead.await(5, TimeUnit.SECONDS));
                String newVersion = publish("new", false);
                assertNotEquals(oldVersion, newVersion);
            } finally { published.countDown(); }
            var snapshot = reading.get(15, TimeUnit.SECONDS);
            assertEquals(oldVersion, snapshot.version());
            assertTrue(snapshot.drugs().values().stream().allMatch(d -> d.name().startsWith("old ")));
            assertTrue(snapshot.combination().matches().stream().allMatch(m -> m.summary().equals("old summary")));
            var current = reader.read(PAIR);
            assertTrue(current.drugs().values().stream().allMatch(d -> d.name().startsWith("new ")));
        }
    }

    private String publish(String label, boolean partial) {
        String version = UUID.randomUUID().toString();
        LocalDate today = LocalDate.of(2026, 11, 11);
        var rows = catalog.all().stream().map(d -> new ReferenceBatch.DrugReference(
                d.itemSeq(), d.modelName(), d.productCode(), label + " " + d.itemSeq(), null, null, null,
                partial && d.itemSeq().equals("197500541") ? "NO_OFFICIAL_RECORD" : "NORMAL",
                partial && d.itemSeq().equals("197500541") ? "NO_OFFICIAL_RECORD" : "COMPLETE",
                null, null, null, null, "{}", LocalDateTime.of(2026, 11, 11, 12, 0))).toList();
        var pairs = ApprovedInteractionPairs.ALL.stream().map(p -> new ReferenceBatch.InteractionReference(
                p.firstItemSeq(), p.secondItemSeq(), "[{\"reason\":null}]", null, label + " summary", HASH, HASH)).toList();
        publisher.publish(sync.begin(false).orElseThrow(), new ReferenceBatch(version, today, rows, pairs));
        return version;
    }
}

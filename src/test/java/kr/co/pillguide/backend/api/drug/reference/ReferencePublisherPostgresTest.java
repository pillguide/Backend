package kr.co.pillguide.backend.api.drug.reference;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.api.member.service.FcmService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

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
class ReferencePublisherPostgresTest {
    private static final String HASH = "a".repeat(64);
    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 11);

    @Autowired ReferencePublisher publisher;
    @Autowired SupportedDrugCatalog catalog;
    @Autowired DrugRepository drugs;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ReferenceSyncService syncService;
    @MockitoBean FcmService fcm;

    @Test
    void replacesCompleteSetInOneCommitAndPreservesExistingIdentityAndUnrelatedDrug() {
        String firstCode = catalog.all().get(0).itemSeq();
        Drug first = drugs.findByCode(firstCode).orElseGet(() -> {
            Drug drug = new Drug("prior name", firstCode, "prior-image");
            drug.setDrugInfo(new DrugInfo(drug, "prior dosage", null, null, "prior effect"));
            return drugs.saveAndFlush(drug);
        });
        Long drugId = first.getDrugId();
        Long infoId = jdbc.queryForObject("select info_id from drug_info where drug_id = ?", Long.class, drugId);
        Drug unrelated = drugs.saveAndFlush(new Drug("unrelated", "unrelated-" + UUID.randomUUID(), null));
        publisher.publish(UUID.randomUUID(), batch("version-1"));

        assertThat(catalog.all().stream().filter(model -> drugs.findByCode(model.itemSeq()).isPresent()).count())
                .isEqualTo(150L);
        assertThat(jdbc.queryForObject("select count(*) from drug_interaction", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("select drug_id from drug where code = ?", Long.class, firstCode)).isEqualTo(drugId);
        assertThat(jdbc.queryForObject("select info_id from drug_info where drug_id = ?", Long.class, drugId)).isEqualTo(infoId);
        assertThat(jdbc.queryForObject("select effect from drug_info where drug_id = ?", String.class, drugId))
                .isEqualTo("effect version-1");
        assertThat(jdbc.queryForObject("select count(*) from drug where drug_id = ?", Long.class, unrelated.getDrugId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from drug_interaction where reference_version = 'version-1'", Long.class))
                .isEqualTo(4L);
    }

    @Test
    void completionFailureRollsBackAllRowsAndKeepsLastGoodVersion() {
        publisher.publish(UUID.randomUUID(), batch("last-good"));
        String firstCode = catalog.all().get(0).itemSeq();
        String priorName = jdbc.queryForObject("select name from drug where code = ?", String.class, firstCode);
        doThrow(new IllegalStateException("failure after data writes"))
                .when(syncService).complete(any(), any(), any());
        assertThatThrownBy(() -> publisher.publish(UUID.randomUUID(), batch("candidate")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select name from drug where code = ?", String.class, firstCode))
                .isEqualTo(priorName);
        assertThat(jdbc.queryForObject("select effect from drug_info i join drug d on d.drug_id = i.drug_id where d.code = ?",
                String.class, firstCode)).isEqualTo("effect last-good");
        assertThat(jdbc.queryForObject("select count(*) from drug_interaction", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("select count(*) from drug_interaction where reference_version = 'last-good'", Long.class))
                .isEqualTo(4L);
    }

    @Test
    void rejectsUnapprovedOrStaleSummaryBeforeTakingRunLock() {
        ReferenceBatch valid = batch("candidate");
        List<ReferenceBatch.InteractionReference> bad = new ArrayList<>(valid.interactions());
        var first = bad.get(0);
        bad.set(0, new ReferenceBatch.InteractionReference(first.firstItemSeq(), first.secondItemSeq(),
                first.evidenceJson(), first.officialReason(), first.reviewedSummary(), HASH, "b".repeat(64)));
        ReferenceBatch invalid = new ReferenceBatch(valid.version(), valid.sourceAsOf(), valid.drugs(), bad);
        assertThatThrownBy(() -> publisher.publish(UUID.randomUUID(), invalid))
                .isInstanceOf(ReferenceValidationException.class).hasMessageContaining("APPROVED_PAIR_INVALID");
        verifyNoInteractions(syncService);
    }

    private ReferenceBatch batch(String version) {
        List<ReferenceBatch.DrugReference> rows = catalog.all().stream().map(model ->
                new ReferenceBatch.DrugReference(model.itemSeq(), model.modelName(), model.productCode(),
                        "Official " + model.itemSeq() + " " + version, "02190", "classification", "ingredient",
                        "NORMAL", "COMPLETE", "effect " + version, "dosage", "storage", "side effects",
                        "{\"schemaVersion\":\"mfds-selected-v1\"}", LocalDateTime.of(2026, 11, 11, 10, 0))).toList();
        List<ReferenceBatch.InteractionReference> pairs = ApprovedInteractionPairs.ALL.stream().map(pair ->
                new ReferenceBatch.InteractionReference(pair.firstItemSeq(), pair.secondItemSeq(), "[]",
                        "official reason", "reviewed reason", HASH, HASH)).toList();
        return new ReferenceBatch(version, AS_OF, rows, pairs);
    }
}

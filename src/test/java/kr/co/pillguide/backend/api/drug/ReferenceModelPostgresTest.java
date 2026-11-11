package kr.co.pillguide.backend.api.drug;

import kr.co.pillguide.backend.api.drug.entity.DrugInteraction;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.repository.DrugInteractionRepository;
import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@TestPropertySource(properties = {
        "spring.datasource.url=${PG_TEST_URL}",
        "spring.datasource.username=${PG_TEST_USER:pillguide_test}",
        "spring.datasource.password=${PG_TEST_PASSWORD:pillguide_test}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class ReferenceModelPostgresTest extends ReferenceModelTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DrugInteractionRepository interactions;
    @Autowired DataSource dataSource;

    @Test
    void concurrentSelectionHasOneRowForMemberAndDrug() throws Exception {
        Fixture f = fixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> insertSelection(f, ready, start));
            var second = pool.submit(() -> insertSelection(f, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(1L, jdbc.queryForObject("select count(*) from member_drug where member_id = ? and drug_id = ?",
                Long.class, f.member().getId(), f.a().getDrugId()));
    }

    @Test
    void scanKeysPreserveMultipleLegacyRowsAndSeparateRepeatedDetections() {
        Fixture f = fixture();
        ScanSession session = scan(f.member(), f.a(), f.b());
        UUID requestId = session.getRequestId();
        assertEquals(3L, jdbc.queryForObject("select count(*) from scan_drug where scan_session_id = ?",
                Long.class, session.getId()));
        assertEquals(2L, jdbc.queryForObject("select count(*) from scan_drug where scan_session_id = ? and drug_drug_id = ?",
                Long.class, session.getId(), f.a().getDrugId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into scan_drug (scan_session_id, drug_drug_id, detection_index) values (?, ?, ?)",
                session.getId(), f.b().getDrugId(), 1));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into scan_session (member_id, scanned_at, request_id) values (?, now(), ?)",
                f.member().getId(), requestId));

        // PostgreSQL UNIQUE allows multiple NULL request IDs for genuinely old scans.
        sessions.saveAndFlush(new ScanSession(f.member()));
        sessions.saveAndFlush(new ScanSession(f.member()));
        assertEquals(2L, jdbc.queryForObject(
                "select count(*) from scan_session where member_id = ? and request_id is null",
                Long.class, f.member().getId()));
    }

    @Test
    void interactionPairRequiresOrderedDistinctIdsAndKeepsAllEvidence() {
        Fixture f = fixture();
        var evidence = "[{\"type\":\"direct\",\"reason\":null},{\"type\":\"ingredient\",\"reason\":\"condition\"}]";
        DrugInteraction saved = interactions.saveAndFlush(new DrugInteraction(f.a(), f.b(), evidence,
                null, null, HASH, null, LocalDate.of(2026, 11, 11), "reference-v1"));
        DrugInteraction loaded = interactions.findById(saved.getId()).orElseThrow();
        assertEquals(evidence, loaded.getEvidenceJson());
        assertNull(loaded.getOfficialReason());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into drug_interaction
                  (low_drug_id, high_drug_id, evidence_json, source_hash, source_as_of, reference_version)
                values (?, ?, '[]', ?, current_date, 'reference-v1')
                """, f.a().getDrugId(), f.b().getDrugId(), HASH));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into drug_interaction
                  (low_drug_id, high_drug_id, evidence_json, source_hash, source_as_of, reference_version)
                values (?, ?, '[]', ?, current_date, 'reference-v1')
                """, f.b().getDrugId(), f.a().getDrugId(), HASH));
        assertEquals(1L, jdbc.queryForObject("select count(*) from drug_interaction where low_drug_id = ? and high_drug_id = ?",
                Long.class, f.a().getDrugId(), f.b().getDrugId()));
    }

    @Test
    void legacyMigrationStopsOnDuplicatesThenPreservesRowsWhenRepeated() throws Exception {
        String schema = "reference_migration_" + UUID.randomUUID().toString().replace("-", "");
        String migration = Files.readString(Path.of("docs/sql/reference-scan-state.sql"));
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("create schema " + schema);
            try {
                sql.execute("set search_path to " + schema);
                sql.execute("create table member (id bigint primary key)");
                sql.execute("create table drug (drug_id bigint primary key, name varchar(255), code varchar(255), image_url varchar(255))");
                sql.execute("create table drug_info (info_id bigint primary key, drug_id bigint, effect text, dosage_method text, storage_method text, side_effects text)");
                sql.execute("create table scan_session (id bigint primary key, member_id bigint, scanned_at timestamp)");
                sql.execute("create table scan_drug (id bigint primary key, scan_session_id bigint, drug_drug_id bigint)");
                sql.execute("insert into member values (1)");
                sql.execute("insert into drug values (10, 'legacy A', '197500541', 'old-image'), (11, 'legacy B', '197500541', null)");
                sql.execute("insert into drug_info values (20, 10, 'old effect', 'old dosage', 'old storage', 'old side effect')");
                sql.execute("insert into scan_session values (30, 1, timestamp '2026-10-01 12:00:00'), (31, 1, timestamp '2026-10-02 12:00:00')");
                sql.execute("insert into scan_drug values (40, 30, 10), (41, 30, 10), (42, 30, 11)");

                assertThrows(java.sql.SQLException.class, () -> sql.execute(migration));
                sql.execute("rollback");
                try (ResultSet rows = sql.executeQuery("select count(*) from information_schema.columns where table_schema = current_schema() and table_name = 'drug' and column_name = 'model_name'")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
                sql.execute("update drug set code = '200001783' where drug_id = 11");
                sql.execute(migration);
                sql.execute(migration);

                try (ResultSet rows = sql.executeQuery("select d.name, d.code, d.image_url, d.model_name, i.effect, i.selected_sources_json from drug d join drug_info i on i.drug_id = d.drug_id where d.drug_id = 10")) {
                    assertTrue(rows.next());
                    assertEquals("legacy A", rows.getString(1));
                    assertEquals("197500541", rows.getString(2));
                    assertEquals("old-image", rows.getString(3));
                    assertNull(rows.getString(4));
                    assertEquals("old effect", rows.getString(5));
                    assertNull(rows.getString(6));
                }
                try (ResultSet rows = sql.executeQuery("select count(*), count(request_id), count(interaction_snapshot) from scan_session where member_id = 1")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                    assertEquals(0, rows.getInt(2));
                    assertEquals(0, rows.getInt(3));
                }
                try (ResultSet rows = sql.executeQuery("select count(*), count(detection_index), count(confidence), count(name_snapshot), count(authorization_status_snapshot) from scan_drug where scan_session_id = 30")) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getInt(1));
                    assertEquals(0, rows.getInt(2));
                    assertEquals(0, rows.getInt(3));
                    assertEquals(0, rows.getInt(4));
                    assertEquals(0, rows.getInt(5));
                }
                try (ResultSet rows = sql.executeQuery("select count(*) from member_drug")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            } finally {
                sql.execute("set search_path to public");
                sql.execute("drop schema " + schema + " cascade");
            }
        }
    }

    private boolean insertSelection(Fixture f, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try { assertTrue(start.await(8, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
        try {
            memberDrugs.saveAndFlush(new MemberDrug(f.member(), f.a()));
            return true;
        } catch (DataIntegrityViolationException expected) {
            return false;
        }
    }
}

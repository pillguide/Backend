package kr.co.pillguide.backend.api.mydrug;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.ScanDrug;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.api.drug.repository.ScanSessionRepository;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.entity.Role;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.AuthService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.mydrug.dto.AddMyDrugsRequest;
import kr.co.pillguide.backend.api.mydrug.repository.MemberDrugRepository;
import kr.co.pillguide.backend.api.mydrug.service.MyDrugService;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import kr.co.pillguide.backend.api.reminder.entity.MedicationReminder;
import kr.co.pillguide.backend.api.reminder.repository.MedicationDoseRepository;
import kr.co.pillguide.backend.api.reminder.repository.MedicationReminderRepository;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import kr.co.pillguide.backend.common.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

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
class MyDrugPostgresTest {
    private static final String A = "197500541";
    private static final String B = "200001783";
    private static final String HASH = "a".repeat(64);

    @Autowired MyDrugService service;
    @Autowired MemberRepository members;
    @Autowired DrugRepository drugs;
    @Autowired ScanSessionRepository scans;
    @Autowired MemberDrugRepository relations;
    @Autowired MedicationReminderRepository reminders;
    @Autowired MedicationDoseRepository doses;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean FcmService fcm;

    private Member owner;
    private Drug drugA;
    private Drug drugB;
    private ScanSession session;
    private List<Long> detectionIds;
    private JwtService.TokenClaims claims;

    @BeforeEach
    void setup() {
        owner = member();
        drugA = drug(A, "My Drug A");
        drugB = drug(B, "My Drug B");
        session = scan(owner, drugA, drugA, drugB);
        detectionIds = session.getDrugs().stream().map(ScanDrug::getId).toList();
        claims = claims(owner);
    }

    @Test
    void aabSelectionCreatesTwoRelationsAndKeepsFirstRegistrationOnRepeat() {
        var selected = new AddMyDrugsRequest(session.getId(), detectionIds);
        var first = service.add(claims, selected);
        assertTrue(first.created());
        assertEquals(2, first.selected().size());
        assertEquals(2L, relationCount(owner.getId()));
        assertEquals(1L, jdbc.queryForObject("select count(*) from member_drug where member_id = ? and drug_id = ?",
                Long.class, owner.getId(), drugA.getDrugId()));

        var repeat = service.add(claims, selected);
        assertFalse(repeat.created());
        assertEquals(first.selected().stream().map(item -> item.memberDrugId()).toList(),
                repeat.selected().stream().map(item -> item.memberDrugId()).toList());
        assertEquals(first.selected().stream().map(item -> item.firstRegisteredAt()).toList(),
                repeat.selected().stream().map(item -> item.firstRegisteredAt()).toList());
    }

    @Test
    void invalidOrForeignDetectionRejectsWholeSelectionBeforeAnyRelationIsWritten() {
        Member another = member();
        ScanSession foreign = scan(another, drugB);
        Long foreignDetection = foreign.getDrugs().getFirst().getId();
        assertThrows(NotFoundException.class, () -> service.add(claims,
                new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst(), foreignDetection))));
        assertEquals(0L, relationCount(owner.getId()));
        assertThrows(BadRequestException.class, () -> service.add(claims,
                new AddMyDrugsRequest(session.getId(), List.of())));
        assertThrows(BadRequestException.class, () -> service.add(claims,
                new AddMyDrugsRequest(session.getId(), List.of(0L))));
        assertThrows(BadRequestException.class, () -> service.add(claims,
                new AddMyDrugsRequest(session.getId(), java.util.Collections.nCopies(301, detectionIds.getFirst()))));
        assertEquals(0L, relationCount(owner.getId()));
    }

    @Test
    void concurrentDuplicateAddHasOneRelationAndOneFirstTime() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var request = new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst()));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> addTogether(ready, start, request));
            var second = workers.submit(() -> addTogether(ready, start, request));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var one = first.get(20, TimeUnit.SECONDS);
            var two = second.get(20, TimeUnit.SECONDS);
            assertEquals(1, (one.created() ? 1 : 0) + (two.created() ? 1 : 0));
            assertEquals(one.selected().getFirst().memberDrugId(), two.selected().getFirst().memberDrugId());
            assertEquals(one.selected().getFirst().firstRegisteredAt(), two.selected().getFirst().firstRegisteredAt());
        }
        assertEquals(1L, relationCount(owner.getId()));
    }

    @Test
    void addWaitsForInFlightRemovalThenCreatesOneNewRelation() throws Exception {
        var request = new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst()));
        Long oldId = service.add(claims, request).selected().getFirst().memberDrugId();
        CountDownLatch removedInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseRemoval = new CountDownLatch(1);
        CountDownLatch addStarted = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var removal = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                service.delete(claims, oldId);
                removedInsideTransaction.countDown();
                try { assertTrue(releaseRemoval.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                return oldId;
            }));
            try {
                assertTrue(removedInsideTransaction.await(5, TimeUnit.SECONDS));
                var addition = workers.submit(() -> {
                    addStarted.countDown();
                    return service.add(claims, request);
                });
                assertTrue(addStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> addition.get(200, TimeUnit.MILLISECONDS));
                releaseRemoval.countDown();
                assertEquals(oldId, removal.get(10, TimeUnit.SECONDS));
                var added = addition.get(10, TimeUnit.SECONDS);
                assertTrue(added.created());
                assertNotEquals(oldId, added.selected().getFirst().memberDrugId());
                assertEquals(1L, relationCount(owner.getId()));
            } finally { releaseRemoval.countDown(); }
        }
    }

    @Test
    void removalWaitsForInFlightAddThenRemovesThatRelation() throws Exception {
        var request = new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst()));
        CountDownLatch addedInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseAddition = new CountDownLatch(1);
        CountDownLatch removalStarted = new CountDownLatch(1);
        AtomicReference<Long> createdId = new AtomicReference<>();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var addition = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                Long id = service.add(claims, request).selected().getFirst().memberDrugId();
                createdId.set(id);
                addedInsideTransaction.countDown();
                try { assertTrue(releaseAddition.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                return id;
            }));
            try {
                assertTrue(addedInsideTransaction.await(5, TimeUnit.SECONDS));
                var removal = workers.submit(() -> {
                    removalStarted.countDown();
                    return service.delete(claims, createdId.get());
                });
                assertTrue(removalStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> removal.get(200, TimeUnit.MILLISECONDS));
                releaseAddition.countDown();
                assertEquals(createdId.get(), addition.get(10, TimeUnit.SECONDS));
                assertEquals(createdId.get(), removal.get(10, TimeUnit.SECONDS));
                assertEquals(0L, relationCount(owner.getId()));
            } finally { releaseAddition.countDown(); }
        }
    }

    @Test
    void listAndDetailReturnOwnCurrentDrugDataWithBoundedPaging() {
        service.add(claims, new AddMyDrugsRequest(session.getId(), detectionIds));
        var page = service.list(owner.getId(), 0, 1);
        assertEquals(1, page.items().size());
        assertEquals(2, page.totalElements());
        assertTrue(page.hasNext());
        assertThrows(BadRequestException.class, () -> service.list(owner.getId(), -1, 20));
        assertThrows(BadRequestException.class, () -> service.list(owner.getId(), 0, 101));

        var relation = relations.findByMember_IdAndDrug_DrugId(owner.getId(), drugA.getDrugId()).orElseThrow();
        jdbc.update("update drug set name = 'Updated current name' where drug_id = ?", drugA.getDrugId());
        var detail = service.detail(owner.getId(), relation.getId());
        assertEquals("Updated current name", detail.drug().name());
        assertEquals(relation.getFirstRegisteredAt(), detail.firstRegisteredAt());
    }

    @Test
    void deleteAndReaddCreateNewRelationAndNewRegistrationTime() {
        var request = new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst()));
        Long firstId = service.add(claims, request).selected().getFirst().memberDrugId();
        jdbc.update("update member_drug set first_registered_at = ? where id = ?",
                LocalDateTime.of(2000, 1, 1, 0, 0), firstId);
        assertEquals(LocalDateTime.of(2000, 1, 1, 0, 0), service.detail(owner.getId(), firstId).firstRegisteredAt());
        service.delete(claims, firstId);
        assertEquals(0L, relationCount(owner.getId()));
        var second = service.add(claims, request).selected().getFirst();
        assertNotEquals(firstId, second.memberDrugId());
        assertTrue(second.firstRegisteredAt().isAfter(LocalDateTime.of(2000, 1, 1, 0, 0)));
    }

    @Test
    void foreignSessionAndRelationAreUniformlyHidden() {
        Member another = member();
        ScanSession foreignSession = scan(another, drugB);
        assertThrows(NotFoundException.class, () -> service.add(claims,
                new AddMyDrugsRequest(foreignSession.getId(),
                        List.of(foreignSession.getDrugs().getFirst().getId()))));
        Long relationId = service.add(claims,
                new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst())))
                .selected().getFirst().memberDrugId();
        assertThrows(NotFoundException.class, () -> service.detail(another.getId(), relationId));
        assertThrows(NotFoundException.class, () -> service.delete(claims(another), relationId));
        assertEquals(1L, relationCount(owner.getId()));
    }

    @Test
    void deletingRelationPreservesScanDrugReminderAndDoseRows() {
        var today = LocalDate.now();
        MedicationReminder reminder = reminders.saveAndFlush(new MedicationReminder(owner,
                new ReminderRequest("Existing reminder", "1 tablet", LocalTime.of(8, 0), today,
                        Set.of(), null, true)));
        MedicationDose dose = doses.saveAndFlush(new MedicationDose(reminder, today));
        Long relationId = service.add(claims,
                new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst())))
                .selected().getFirst().memberDrugId();
        service.delete(claims, relationId);
        assertEquals(0L, relationCount(owner.getId()));
        assertTrue(scans.existsById(session.getId()));
        assertEquals(3L, jdbc.queryForObject("select count(*) from scan_drug where scan_session_id = ?",
                Long.class, session.getId()));
        assertTrue(drugs.existsById(drugA.getDrugId()));
        assertTrue(reminders.existsById(reminder.getId()));
        assertTrue(doses.existsById(dose.getId()));
    }

    @Test
    void revokedAccessCannotAddOrDeleteAfterLogout() {
        var tokens = auth.loginSuccess(owner);
        var active = claims(owner);
        Long relationId = service.add(active,
                new AddMyDrugsRequest(session.getId(), List.of(detectionIds.getFirst())))
                .selected().getFirst().memberDrugId();
        auth.logout(tokens.refreshToken());
        assertThrows(UnauthorizedException.class, () -> service.add(active,
                new AddMyDrugsRequest(session.getId(), List.of(detectionIds.get(2)))));
        assertThrows(UnauthorizedException.class, () -> service.delete(active, relationId));
        assertEquals(1L, relationCount(owner.getId()));
    }

    private MyDrugService.AddOutcome addTogether(CountDownLatch ready, CountDownLatch start,
                                                  AddMyDrugsRequest request) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return service.add(claims, request);
    }

    private Member member() {
        return members.saveAndFlush(Member.builder().provider("mydrug-test")
                .oauthId(UUID.randomUUID().toString()).name("Member").role(Role.ROLE_USER).build());
    }

    private Drug drug(String code, String name) {
        Drug drug = drugs.findByCode(code).orElseGet(() -> drugs.saveAndFlush(new Drug(name, code, null)));
        jdbc.update("update drug set name = ? where drug_id = ?", name, drug.getDrugId());
        return drug;
    }

    private ScanSession scan(Member member, Drug... detected) {
        ScanSession created = new ScanSession(member, UUID.randomUUID(), HASH, 100, 100,
                HASH, HASH, "reference-v1", "{}");
        for (int index = 0; index < detected.length; index++) {
            created.addDrug(new ScanDrug(created, detected[index], index + 1, .9,
                    .1, .1, .4, .4, false, detected[index].getName(), "COMPLETE", "NORMAL"));
        }
        return scans.saveAndFlush(created);
    }

    private JwtService.TokenClaims claims(Member member) {
        return new JwtService.TokenClaims(member.getId(), member.getAuthVersion(), Instant.now().plusSeconds(3600));
    }

    private long relationCount(Long memberId) {
        return jdbc.queryForObject("select count(*) from member_drug where member_id = ?", Long.class, memberId);
    }
}

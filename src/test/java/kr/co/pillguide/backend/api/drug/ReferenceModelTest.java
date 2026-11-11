package kr.co.pillguide.backend.api.drug;

import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.entity.ScanDrug;
import kr.co.pillguide.backend.api.drug.entity.ScanSession;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.api.drug.repository.ScanSessionRepository;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.mydrug.entity.MemberDrug;
import kr.co.pillguide.backend.api.mydrug.repository.MemberDrugRepository;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import kr.co.pillguide.backend.api.reminder.entity.MedicationReminder;
import kr.co.pillguide.backend.api.reminder.repository.MedicationReminderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReferenceModelTest {
    static final String HASH = "a".repeat(64);
    @Autowired MemberRepository members;
    @Autowired DrugRepository drugs;
    @Autowired ScanSessionRepository sessions;
    @Autowired MemberDrugRepository memberDrugs;
    @Autowired MedicationReminderRepository reminders;
    @MockitoBean FcmService fcm;

    record Fixture(Member member, Drug a, Drug b) { }

    Fixture fixture() {
        String unique = UUID.randomUUID().toString();
        Member member = members.saveAndFlush(Member.newSocialMember("model-test", "model@example.invalid", "test", unique));
        Drug a = drugs.saveAndFlush(new Drug("old A", "A-" + unique, null));
        Drug b = drugs.saveAndFlush(new Drug("old B", "B-" + unique, null));
        return new Fixture(member, a, b);
    }

    ScanSession scan(Member member, Drug a, Drug b) {
        ScanSession session = new ScanSession(member, UUID.randomUUID(), HASH, 800, 600,
                HASH, HASH, "reference-v1", "{\"pairs\":[{\"status\":\"reviewed\"}]}");
        session.addDrug(new ScanDrug(session, a, 0, .9, .1, .1, .2, .2,
                false, "old A", "available", "authorized"));
        session.addDrug(new ScanDrug(session, a, 1, .8, .3, .3, .4, .4,
                true, "old A", "available", "authorized"));
        session.addDrug(new ScanDrug(session, b, 2, .7, .5, .5, .6, .6,
                false, "old B", "missing", "unknown"));
        return sessions.saveAndFlush(session);
    }

    @Test
    void repeatedDetectionKeepsThreeCardsAndThenSnapshotAfterReferenceUpdate() {
        Fixture f = fixture();
        ScanSession saved = scan(f.member(), f.a(), f.b());
        Long scanId = saved.getId();
        Drug a = drugs.findById(f.a().getDrugId()).orElseThrow();
        a.updateReferenceData("new official A", "model A", "product A", "class", "classification",
                "ingredient", "authorized", "current", LocalDate.of(2026, 11, 11));
        drugs.saveAndFlush(a);

        ScanSession reloaded = sessions.findByIdAndMember_Id(scanId, f.member().getId()).orElseThrow();
        assertEquals(3, reloaded.getDrugs().size());
        assertEquals(2, reloaded.getDrugs().stream().filter(d -> d.getDrug().getDrugId().equals(a.getDrugId())).count());
        assertEquals(Set.of(0, 1, 2), reloaded.getDrugs().stream()
                .map(ScanDrug::getDetectionIndex).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.List.of(0, 1, 2), reloaded.getDrugs().stream()
                .map(ScanDrug::getDetectionIndex).toList());
        assertThrows(UnsupportedOperationException.class, () -> reloaded.getDrugs().clear());
        assertEquals("old A", reloaded.getDrugs().stream().filter(d -> d.getDetectionIndex() == 0)
                .findFirst().orElseThrow().getNameSnapshot());
        assertEquals("available", reloaded.getDrugs().stream().filter(d -> d.getDetectionIndex() == 0)
                .findFirst().orElseThrow().getInfoStatusSnapshot());
        assertEquals("authorized", reloaded.getDrugs().stream().filter(d -> d.getDetectionIndex() == 0)
                .findFirst().orElseThrow().getAuthorizationStatusSnapshot());
        assertEquals("reference-v1", reloaded.getReferenceVersion());
        assertEquals("{\"pairs\":[{\"status\":\"reviewed\"}]}", reloaded.getInteractionSnapshot());
        assertEquals("new official A", drugs.findById(a.getDrugId()).orElseThrow().getName());
    }

    @Test
    void removingOneMemberDrugLeavesMasterScanAndMedicationReminder() {
        Fixture f = fixture();
        Long scanId = scan(f.member(), f.a(), f.b()).getId();
        Long reminderId = reminders.saveAndFlush(new MedicationReminder(f.member(),
                new ReminderRequest("medicine", "one", LocalTime.NOON, LocalDate.of(2026, 11, 11),
                        Set.of(), "", true))).getId();
        MemberDrug selected = memberDrugs.saveAndFlush(new MemberDrug(f.member(), f.a()));
        assertNotNull(selected.getFirstRegisteredAt());

        memberDrugs.deleteById(selected.getId());
        assertTrue(memberDrugs.findById(selected.getId()).isEmpty());
        assertTrue(drugs.findById(f.a().getDrugId()).isPresent());
        assertEquals(3, sessions.findByIdAndMember_Id(scanId, f.member().getId()).orElseThrow().getDrugs().size());
        assertTrue(reminders.findById(reminderId).isPresent());
    }

    @Test
    void legacyScanKeepsUnknownMetadataNullInsteadOfInventingDetectionData() {
        Fixture f = fixture();
        ScanSession legacy = new ScanSession(f.member());
        legacy.addDrug(new ScanDrug(legacy, f.a()));
        legacy = sessions.saveAndFlush(legacy);
        ScanSession loaded = sessions.findByIdAndMember_Id(legacy.getId(), f.member().getId()).orElseThrow();
        assertTrue(loaded.isLegacy());
        assertNull(loaded.getRequestId());
        assertNull(loaded.getInteractionSnapshot());
        assertEquals(1, loaded.getDrugs().size());
        assertNull(loaded.getDrugs().getFirst().getDetectionIndex());
        assertNull(loaded.getDrugs().getFirst().getConfidence());
        assertNull(loaded.getDrugs().getFirst().getNameSnapshot());
        assertNull(loaded.getDrugs().getFirst().getAuthorizationStatusSnapshot());
    }

    @Test
    void legacyAndNewDetectionShapesCannotBeMixed() {
        Fixture f = fixture();
        ScanSession legacy = new ScanSession(f.member());
        ScanSession current = new ScanSession(f.member(), UUID.randomUUID(), HASH, 800, 600,
                HASH, HASH, "reference-v1", "{}");
        assertThrows(IllegalArgumentException.class, () -> new ScanDrug(current, f.a()));
        assertThrows(IllegalArgumentException.class, () -> new ScanDrug(legacy, f.a(), 0,
                .9, .1, .1, .2, .2, false, "A", "available", "authorized"));
    }
}

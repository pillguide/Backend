package kr.co.pillguide.backend.api.reminder;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import kr.co.pillguide.backend.api.member.service.FcmTokenService;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import kr.co.pillguide.backend.api.reminder.repository.MedicationDoseRepository;
import kr.co.pillguide.backend.api.reminder.repository.MedicationReminderRepository;
import kr.co.pillguide.backend.api.reminder.service.MedicationService;
import kr.co.pillguide.backend.api.reminder.service.MedicationNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import kr.co.pillguide.backend.common.security.SecurityMember;
import kr.co.pillguide.backend.api.member.service.AuthService;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class MedicationIntegrationTest {
    @Autowired MedicationService medication;
    @Autowired MedicationNotificationService notifications;
    @Autowired FcmTokenService tokens;
    @Autowired MemberRepository members;
    @Autowired MedicationReminderRepository reminders;
    @Autowired MedicationDoseRepository doses;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext webContext;
    @Autowired AuthService auth;
    @MockitoBean(name = "medicationClock") Clock clock;
    @MockitoBean FcmService sender;
    final AtomicReference<Instant> time = new AtomicReference<>();
    Long memberId;
    LocalDate today = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        time.set(Instant.parse("2026-11-10T01:00:00Z"));
        when(clock.instant()).thenAnswer(invocation -> time.get());
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString()))
                .thenReturn(new FcmService.SendResult(FcmService.Outcome.ACCEPTED, Duration.ZERO));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            doses.deleteAll();
            reminders.deleteAll();
            members.deleteAll();
            memberId = members.saveAndFlush(Member.newSocialMember("test", "test@example.invalid", "test", UUID.randomUUID().toString())).getId();
        });
        tokens.register(memberId, "test-device");
    }

    ReminderRequest request(LocalDate date, LocalTime time, boolean enabled) {
        return new ReminderRequest("medicine", "one", time, date, Set.of(), "memo", enabled);
    }

    @Test
    void preparingWithoutSendingDoesNotConsumeTheNotification() {
        medication.create(memberId, request(today, LocalTime.of(10, 0), true));
        assertEquals(1, notifications.prepare(memberId).size());
        assertEquals(1, notifications.prepare(memberId).size());
    }

    Long due() {
        medication.create(memberId, request(today, LocalTime.of(10, 0), true));
        return notifications.prepare(memberId).getFirst();
    }

    @Test
    void successfulSubmissionIsNotRepeated() {
        Long id = due();
        notifications.send(memberId, id);
        notifications.send(memberId, id);
        assertTrue(notifications.prepare(memberId).isEmpty());
        verify(sender, times(1)).sendMedicationNotification("test-device", id, "medicine");
    }

    @Test
    void temporaryFailureIsRetriedAfterItsDelay() {
        Long id = due();
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString()))
                .thenReturn(new FcmService.SendResult(FcmService.Outcome.RETRY, Duration.ofSeconds(60)))
                .thenReturn(new FcmService.SendResult(FcmService.Outcome.ACCEPTED, Duration.ZERO));
        notifications.send(memberId, id);
        assertTrue(notifications.prepare(memberId).isEmpty());
        time.updateAndGet(value -> value.plusSeconds(60));
        assertEquals(List.of(id), notifications.prepare(memberId));
        notifications.send(memberId, id);
        assertTrue(notifications.prepare(memberId).isEmpty());
    }

    @Test
    void fiveMinuteBoundaryIsInclusiveAndThenExpires() {
        Long id = due();
        time.updateAndGet(value -> value.plusSeconds(300));
        assertEquals(List.of(id), notifications.prepare(memberId));
        time.updateAndGet(value -> value.plusSeconds(1));
        notifications.send(memberId, id);
        assertTrue(notifications.prepare(memberId).isEmpty());
        verifyNoInteractions(sender);
    }

    @Test
    void successfulDoseRemainsSuppressedAfterCheckAndUndo() {
        Long id = due();
        notifications.send(memberId, id);
        medication.check(memberId, id, true);
        medication.check(memberId, id, false);
        assertTrue(notifications.prepare(memberId).isEmpty());
    }

    @Test
    void checkAndUndoReactivatesAnUnsentDose() {
        Long id = due();
        medication.check(memberId, id, true);
        notifications.send(memberId, id);
        verifyNoInteractions(sender);
        medication.check(memberId, id, false);
        assertEquals(List.of(id), notifications.prepare(memberId));
    }

    @Test
    void disableAndEnableReactivatesOnlyAnUnsentDose() {
        Long id = due();
        Long reminderId = medication.day(memberId, today).doses().getFirst().reminderId();
        medication.enabled(memberId, reminderId, false);
        notifications.send(memberId, id);
        verifyNoInteractions(sender);
        medication.enabled(memberId, reminderId, true);
        assertEquals(List.of(id), notifications.prepare(memberId));
        notifications.send(memberId, id);
        medication.enabled(memberId, reminderId, false);
        medication.enabled(memberId, reminderId, true);
        assertTrue(notifications.prepare(memberId).isEmpty());
    }

    @Test
    void revokedTokenCancelsPreviouslyPreparedDelivery() {
        Long id = due();
        tokens.delete(memberId);
        notifications.send(memberId, id);
        verifyNoInteractions(sender);
    }

    @Test
    void transferringTokenCancelsPreviousOwnersPreparedDelivery() {
        Long id = due();
        Long other = members.saveAndFlush(Member.newSocialMember("other", "other@example.invalid", "test", "other")).getId();
        tokens.register(other, "test-device");
        notifications.send(memberId, id);
        verifyNoInteractions(sender);
        assertNull(members.findById(memberId).orElseThrow().getFcmToken());
        assertEquals("test-device", members.findById(other).orElseThrow().getFcmToken());
    }

    @Test
    void invalidTokenCanBeReplacedWithinWindow() {
        Long id = due();
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString()))
                .thenReturn(new FcmService.SendResult(FcmService.Outcome.INVALID_TOKEN, Duration.ZERO));
        notifications.send(memberId, id);
        assertNull(members.findById(memberId).orElseThrow().getFcmToken());
        tokens.register(memberId, "new-device");
        time.updateAndGet(value -> value.plusSeconds(30));
        assertEquals(List.of(id), notifications.prepare(memberId));
    }

    @Test
    void permanentErrorAndLongRetryAfterDoNotLoop() {
        Long id = due();
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString()))
                .thenReturn(new FcmService.SendResult(FcmService.Outcome.PERMANENT_FAILURE, Duration.ZERO));
        notifications.send(memberId, id);
        assertTrue(notifications.prepare(memberId).isEmpty());
        Long second = due();
        when(sender.sendMedicationNotification(anyString(), anyLong(), anyString()))
                .thenReturn(new FcmService.SendResult(FcmService.Outcome.RETRY, Duration.ofDays(1)));
        notifications.send(memberId, second);
        assertTrue(notifications.prepare(memberId).isEmpty());
    }

    @Test
    void oldAttemptTimestampDoesNotPretendToBeSuccess() {
        Long id = due();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                doses.findById(id).orElseThrow().notificationAttempted(LocalDateTime.of(2026, 11, 10, 10, 0)));
        assertEquals(List.of(id), notifications.prepare(memberId));
    }

    @Test
    void deletedReminderKeepsHistoryButCancelsDelivery() {
        Long id = due();
        var record = medication.day(memberId, today).doses().getFirst();
        medication.delete(memberId, List.of(record.reminderId()));
        notifications.send(memberId, id);
        verifyNoInteractions(sender);
        assertEquals(record, medication.day(memberId, today).doses().getFirst());
    }

    @Test
    void elapsedAndTakenSnapshotsSurviveEdits() {
        var reminder = medication.create(memberId, request(today, LocalTime.of(8, 0), true));
        var original = medication.day(memberId, today).doses().getFirst();
        medication.update(memberId, reminder.id(), request(today, LocalTime.of(20, 0), true));
        assertEquals(original, medication.day(memberId, today).doses().getFirst());
        assertNull(medication.home(memberId).nextDose());
    }

    @Test
    void earlyCompletionSurvivesScheduleEditAndChangeAppliesTomorrow() {
        var reminder = medication.create(memberId, new ReminderRequest("original", "one", LocalTime.of(12, 0),
                today, Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), "memo", true));
        var original = medication.day(memberId, today).doses().getFirst();
        var checked = medication.check(memberId, original.id(), true);
        medication.update(memberId, reminder.id(), new ReminderRequest("updated", "two", LocalTime.of(20, 0),
                today, Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), "new memo", true));
        assertEquals(checked, medication.day(memberId, today).doses().getFirst());
        var tomorrow = medication.day(memberId, today.plusDays(1)).doses().getFirst();
        assertEquals("updated", tomorrow.medicationName());
        assertEquals(today.plusDays(1).atTime(20, 0), tomorrow.scheduledAt());
        assertThrows(BadRequestException.class, () -> medication.check(memberId, tomorrow.id(), true));
        assertThrows(BadRequestException.class, () -> medication.check(memberId, tomorrow.id(), false));
        assertEquals(1, medication.home(memberId).streak());
    }

    @Test
    void nextDoseSkipsOverdueAndKeepsStableOrderForSameTime() {
        medication.create(memberId, request(today, LocalTime.of(8, 0), true));
        medication.create(memberId, request(today, LocalTime.of(12, 0), false));
        medication.create(memberId, request(today, LocalTime.of(12, 0), true));
        var home = medication.home(memberId);
        assertEquals(3, home.progress().remaining());
        assertEquals(home.doses().get(1), home.nextDose());
        medication.check(memberId, home.nextDose().id(), true);
        assertEquals(home.doses().get(2), medication.home(memberId).nextDose());
    }

    @Test
    void futureSnapshotsAreRegeneratedWithoutDuplicates() {
        var reminder = medication.create(memberId, request(today, LocalTime.of(12, 0), true));
        medication.day(memberId, today);
        medication.update(memberId, reminder.id(), request(today, LocalTime.of(13, 0), false));
        var home = medication.home(memberId);
        assertEquals(1, home.doses().size());
        assertEquals(today.atTime(13, 0), home.nextDose().scheduledAt());
        assertEquals(1, medication.home(memberId).doses().size());
    }

    @Test
    void midnightLocksYesterdayChecksWhileRetryWindowContinues() {
        time.set(Instant.parse("2026-11-10T14:58:00Z"));
        medication.create(memberId, request(today, LocalTime.of(23, 58), true));
        Long id = notifications.prepare(memberId).getFirst();
        time.set(Instant.parse("2026-11-10T15:01:00Z"));
        assertEquals(List.of(id), notifications.prepare(memberId));
        assertThrows(BadRequestException.class, () -> medication.check(memberId, id, true));
    }

    @Test
    void duplicateChecksPreserveFirstTimestampAndOwnership() {
        Long id = due();
        var checked = medication.check(memberId, id, true);
        time.updateAndGet(value -> value.plusSeconds(30));
        assertEquals(checked.takenAt(), medication.check(memberId, id, true).takenAt());
        Long other = members.saveAndFlush(Member.newSocialMember("other", "other@example.invalid", "test", "other")).getId();
        assertThrows(NotFoundException.class, () -> medication.check(other, id, true));
    }

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    }

    SecurityMember principal() {
        return new SecurityMember(members.findById(memberId).orElseThrow());
    }

    @Test
    void medicationApiPreservesPathsPayloadsAndValidation() throws Exception {
        var mvc = mvc();
        String json = """
                {"medicationName":"medicine","dosage":"one","time":"12:00",
                 "startDate":"2026-11-10","repeatDays":[],"memo":"memo","enabled":false}
                """;
        mvc.perform(post("/api/v1/medication/reminders").with(user(principal()))
                .contentType("application/json").content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.medicationName").value("medicine"));
        Long reminderId = medication.list(memberId).getFirst().id();
        mvc.perform(get("/api/v1/medication/reminders/{id}", reminderId).with(user(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(get("/api/v1/medication/home").with(user(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.progress.total").value(1))
                .andExpect(jsonPath("$.data.nextDose.medicationName").value("medicine"));
        mvc.perform(patch("/api/v1/medication/reminders/{id}/enabled", reminderId).with(user(principal()))
                .contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
        mvc.perform(get("/api/v1/medication/records/weekly").param("date", today.toString()).with(user(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.days.length()").value(7));
        mvc.perform(post("/api/v1/medication/reminders").with(user(principal()))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/v1/medication/home")).andExpect(status().isUnauthorized());
    }

    @Test
    void checkApiReturns400ForPastAnd404ForOtherMembers() throws Exception {
        Long id = due();
        var mvc = mvc();
        mvc.perform(put("/api/v1/medication/records/{id}/check", id).with(user(principal()))
                .contentType("application/json").content("{\"taken\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("TAKEN"));
        var other = members.saveAndFlush(Member.newSocialMember("other", "other@example.invalid", "test", "other"));
        mvc.perform(put("/api/v1/medication/records/{id}/check", id).with(user(new SecurityMember(other)))
                .contentType("application/json").content("{\"taken\":false}"))
                .andExpect(status().isNotFound());
        time.updateAndGet(value -> value.plusSeconds(86400));
        mvc.perform(put("/api/v1/medication/records/{id}/check", id).with(user(principal()))
                .contentType("application/json").content("{\"taken\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenAndLogoutApisRevokePreparedNotifications() throws Exception {
        Long id = due();
        var mvc = mvc();
        mvc.perform(put("/api/v1/member/fcm-token").with(user(principal()))
                .contentType("application/json").content("{\"token\":\"replacement\"}"))
                .andExpect(status().isOk());
        var login = auth.loginSuccess(members.findById(memberId).orElseThrow());
        mvc.perform(get("/api/v1/medication/home").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.progress.total").value(1));
        mvc.perform(post("/api/v1/auth/logout").header("X-Refresh-Token", login.refreshToken()))
                .andExpect(status().isOk());
        notifications.send(memberId, id);
        verifyNoInteractions(sender);
        mvc.perform(delete("/api/v1/member/fcm-token").with(user(principal())))
                .andExpect(status().isOk());
    }
}

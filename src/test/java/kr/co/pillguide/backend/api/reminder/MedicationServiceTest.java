package kr.co.pillguide.backend.api.reminder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import kr.co.pillguide.backend.api.reminder.entity.MedicationReminder;
import kr.co.pillguide.backend.api.reminder.repository.MedicationDoseRepository;
import kr.co.pillguide.backend.api.reminder.repository.MedicationReminderRepository;
import kr.co.pillguide.backend.api.reminder.service.MedicationService;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MedicationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDate today = LocalDate.now(clock);
    private final MedicationDoseRepository doses = mock(MedicationDoseRepository.class);

    private MedicationDose dose(LocalDate date, LocalTime time, boolean enabled) {
        var reminder = new MedicationReminder(null, new ReminderRequest("medicine", "one", time, date, Set.of(), "", enabled));
        return new MedicationDose(reminder, date);
    }

    private MedicationService service(List<MedicationDose> rows) {
        var em = mock(EntityManager.class);
        when(em.find(Member.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(mock(Member.class));
        var reminders = mock(MedicationReminderRepository.class);
        when(reminders.findAllByMember_IdAndDeletedFalseOrderByTimeAscIdAsc(1L)).thenReturn(List.of());
        when(doses.findAllByReminder_Member_IdAndDateBetweenOrderByScheduledAtAscIdAsc(1L, LocalDate.of(1970, 1, 1), today.plusDays(7))).thenReturn(rows);
        return new MedicationService(em, reminders, doses, clock);
    }

    @Test
    void tomorrowIsNotTheNextDoseOnTodaysHome() {
        var tomorrow = dose(today.plusDays(1), LocalTime.of(8, 0), true);
        assertNull(service(List.of(tomorrow)).home(1L).nextDose());
    }

    @Test
    void disablingPushKeepsNextDoseAndProgress() {
        var upcoming = dose(today, LocalTime.of(11, 0), false);
        var result = service(List.of(upcoming)).home(1L);
        assertEquals(1, result.progress().remaining());
        assertEquals(today.atTime(11, 0), result.nextDose().scheduledAt());
    }

    @Test
    void pastDatesCannotBeCheckedOrUnchecked() {
        var past = dose(today.minusDays(1), LocalTime.of(8, 0), true);
        when(doses.findByIdAndReminder_Member_Id(2L, 1L)).thenReturn(Optional.of(past));
        var service = service(List.of());
        assertThrows(BadRequestException.class, () -> service.check(1L, 2L, true));
        assertThrows(BadRequestException.class, () -> service.check(1L, 2L, false));
    }
}

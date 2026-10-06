package kr.co.pillguide.backend.api.reminder.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import kr.co.pillguide.backend.api.reminder.dto.MedicationResponses.*;
import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import kr.co.pillguide.backend.api.reminder.entity.MedicationReminder;
import kr.co.pillguide.backend.api.reminder.repository.MedicationDoseRepository;
import kr.co.pillguide.backend.api.reminder.repository.MedicationReminderRepository;
import kr.co.pillguide.backend.common.exception.BadRequestException;
import kr.co.pillguide.backend.common.exception.NotFoundException;
import kr.co.pillguide.backend.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class MedicationService {

    private final EntityManager em;
    private final MedicationReminderRepository reminders;
    private final MedicationDoseRepository doses;
    private final Clock clock;

    public MedicationService(
            EntityManager em,
            MedicationReminderRepository reminders,
            MedicationDoseRepository doses,
            @Qualifier("medicationClock") Clock clock
    ) {
        this.em = em;
        this.reminders = reminders;
        this.doses = doses;
        this.clock = clock;
    }

    // 같은 회원의 동시 요청을 순서대로 처리해 기록 중복 생성 방지
    // 이후 FCM 서비스에서도 사용
    public Member lockMember(Long memberId) {
        if (memberId == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }

        Member member = em.find(
                Member.class,
                memberId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (member == null) {
            throw new NotFoundException("회원을 찾을 수 없습니다.");
        }

        return member;
    }

    // 회원의 복약 알림 목록 조회
    public List<Reminder> list(Long memberId) {
        lockMember(memberId);

        return active(memberId).stream()
                .map(Reminder::from)
                .toList();
    }

    // 복약 알림 등록
    public Reminder create(
            Long memberId,
            ReminderRequest request
    ) {
        Member member = lockMember(memberId);
        validateStart(request);

        MedicationReminder reminder =
                new MedicationReminder(member, request);

        return Reminder.from(reminders.save(reminder));
    }

    // 복약 알림 수정
    public Reminder update(
            Long memberId,
            Long id,
            ReminderRequest request
    ) {
        lockMember(memberId);

        MedicationReminder reminder = owned(memberId, id);

        // 기존 시작일은 유지 가능하지만 새 시작일을 과거로 변경할 수는 없음
        if (!request.startDate().equals(reminder.getStartDate())) {
            validateStart(request);
        }

        // 수정 전 일정으로 오늘까지의 기록을 보존
        generate(reminder, today());

        // 미래 미복용 기록은 제거하고 변경된 설정으로 다시 생성
        clearFuture(reminder);
        reminder.update(request);

        return Reminder.from(reminder);
    }

    // 푸시 알림 활성화/비활성화
    // 비활성화해도 복약 체크리스트는 유지
    public Reminder enabled(
            Long memberId,
            Long id,
            boolean enabled
    ) {
        lockMember(memberId);

        MedicationReminder reminder = owned(memberId, id);
        reminder.setEnabled(enabled);

        return Reminder.from(reminder);
    }

    // 단일 삭제와 선택 삭제에서 공통 사용
    public void delete(
            Long memberId,
            List<Long> ids
    ) {
        lockMember(memberId);

        // 모든 알림의 소유권을 먼저 확인
        List<MedicationReminder> selected = ids.stream()
                .distinct()
                .sorted()
                .map(id -> owned(memberId, id))
                .toList();

        for (MedicationReminder reminder : selected) {
            generate(reminder, today());
            clearFuture(reminder);

            // 과거 기록 보존을 위해 실제 행을 삭제하지 않음
            reminder.delete();
        }
    }

    // 특정 날짜의 복약 체크리스트 조회
    public Day day(
            Long memberId,
            LocalDate date
    ) {
        lockMember(memberId);
        validateRange(date, date);

        return dayOf(
                date,
                records(memberId, date, date),
                now()
        );
    }

    // 전달받은 날짜가 포함된 주의 기록 조회
    public Week week(
            Long memberId,
            LocalDate date
    ) {
        lockMember(memberId);

        LocalDate from = date.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate to = from.plusDays(6);

        validateRange(from, to);

        List<MedicationDose> records =
                records(memberId, from, to);

        LocalDateTime currentTime = now();

        List<Day> days = from.datesUntil(to.plusDays(1))
                .map(day -> dayOf(
                        day,
                        records.stream()
                                .filter(record ->
                                        record.getDate().equals(day)
                                )
                                .toList(),
                        currentTime
                ))
                .toList();

        return new Week(
                from,
                to,
                Progress.of(records),
                days
        );
    }

    // 복약 완료 또는 완료 취소
    public Dose check(
            Long memberId,
            Long doseId,
            boolean taken
    ) {
        lockMember(memberId);

        MedicationDose dose = doses
                .findByIdAndReminder_Member_Id(doseId, memberId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "복약 기록을 찾을 수 없습니다."
                        )
                );

        if (dose.getDate().isAfter(today())) {
            throw new BadRequestException(
                    "미래 날짜의 복약은 체크할 수 없습니다."
            );
        }

        LocalDateTime currentTime = now();
        dose.check(taken, currentTime);

        return Dose.from(dose, currentTime);
    }

    // 홈 화면: 오늘 진행률, 연속 완료 일수, 다음 알림, 체크리스트
    public Home home(Long memberId) {
        lockMember(memberId);

        LocalDate currentDate = today();
        LocalDateTime currentTime = now();

        // 과거 기록과 향후 7일의 일정을 조회
        List<MedicationDose> all = records(
                memberId,
                LocalDate.of(1970, 1, 1),
                currentDate.plusDays(7)
        );

        List<MedicationDose> daily = all.stream()
                .filter(dose ->
                        dose.getDate().equals(currentDate)
                )
                .toList();

        // 예정 시각순으로 조회된 기록 중 가장 가까운 활성 알림
        Dose next = all.stream()
                .filter(dose ->
                        dose.getTakenAt() == null
                                && dose.getReminder().isEnabled()
                                && !dose.getReminder().isDeleted()
                                && !dose.getScheduledAt()
                                .isBefore(currentTime)
                )
                .findFirst()
                .map(dose -> Dose.from(dose, currentTime))
                .orElse(null);

        Map<LocalDate, List<MedicationDose>> byDate =
                new HashMap<>();

        for (MedicationDose dose : all) {
            byDate.computeIfAbsent(
                    dose.getDate(),
                    ignored -> new java.util.ArrayList<>()
            ).add(dose);
        }

        // 오늘 모두 완료했다면 오늘부터, 아니면 어제부터 계산
        LocalDate cursor = complete(byDate.get(currentDate))
                ? currentDate
                : currentDate.minusDays(1);

        int streak = 0;

        while (complete(byDate.get(cursor))) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        return new Home(
                currentDate,
                Progress.of(daily),
                streak,
                next,
                daily.stream()
                        .map(dose -> Dose.from(dose, currentTime))
                        .toList()
        );
    }

    // 필요한 날짜까지 기록을 생성하고 조회
    // FCM 서비스에서는 lockMember 호출 후 같은 트랜잭션에서 사용
    public List<MedicationDose> records(
            Long memberId,
            LocalDate from,
            LocalDate to
    ) {
        for (MedicationReminder reminder : active(memberId)) {
            generate(reminder, to);
        }

        return doses
                .findAllByReminder_Member_IdAndDateBetweenOrderByScheduledAtAscIdAsc(
                        memberId,
                        from,
                        to
                );
    }

    // 반복 요일 또는 일회성 일정에 해당하는 날짜의 기록 생성
    private void generate(
            MedicationReminder reminder,
            LocalDate through
    ) {
        LocalDate start =
                reminder.getGeneratedThrough().plusDays(1);

        if (start.isBefore(reminder.getStartDate())) {
            start = reminder.getStartDate();
        }

        for (
                LocalDate date = start;
                !date.isAfter(through);
                date = date.plusDays(1)
        ) {
            if (reminder.occursOn(date)
                    && !doses.existsByReminder_IdAndDate(
                    reminder.getId(),
                    date
            )) {
                doses.save(new MedicationDose(reminder, date));
            }
        }

        if (through.isAfter(reminder.getGeneratedThrough())) {
            reminder.generatedThrough(through);
        }
    }

    // 수정/삭제 시 미래 미복용 기록 정리
    private void clearFuture(MedicationReminder reminder) {
        List<MedicationDose> future = doses
                .findAllByReminder_IdAndScheduledAtAfterAndTakenAtIsNull(
                        reminder.getId(),
                        now()
                );

        doses.deleteAll(future);
        doses.flush();

        // 변경된 설정으로 오늘 이후 일정을 다시 생성할 수 있게 초기화
        reminder.generatedThrough(today().minusDays(1));
    }

    // 삭제되지 않은 알림 목록
    private List<MedicationReminder> active(Long memberId) {
        return reminders
                .findAllByMember_IdAndDeletedFalseOrderByTimeAscIdAsc(
                        memberId
                );
    }

    // 본인 소유의 알림만 조회
    private MedicationReminder owned(
            Long memberId,
            Long id
    ) {
        return reminders
                .findByIdAndMember_IdAndDeletedFalse(id, memberId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "복약 알림을 찾을 수 없습니다."
                        )
                );
    }

    // 신규 또는 변경된 시작일은 오늘부터 1년 이내
    private void validateStart(ReminderRequest request) {
        if (request.startDate().isBefore(today())) {
            throw new BadRequestException(
                    "시작일은 오늘 이후여야 합니다."
            );
        }

        if (request.startDate().isAfter(today().plusYears(1))) {
            throw new BadRequestException(
                    "시작일은 1년 이내여야 합니다."
            );
        }
    }

    // 조회 가능한 날짜 범위
    private void validateRange(
            LocalDate from,
            LocalDate to
    ) {
        if (from.isBefore(LocalDate.of(1970, 1, 1))
                || to.isAfter(today().plusDays(7))) {
            throw new BadRequestException(
                    "조회는 1970년 이후부터 오늘 기준 7일 뒤까지 가능합니다."
            );
        }
    }

    // 복약 계획이 있고 모든 기록이 완료된 날인지 확인
    private boolean complete(List<MedicationDose> records) {
        return records != null
                && !records.isEmpty()
                && records.stream()
                .allMatch(dose ->
                        dose.getTakenAt() != null
                );
    }

    // 날짜별 응답 생성
    private Day dayOf(
            LocalDate date,
            List<MedicationDose> records,
            LocalDateTime currentTime
    ) {
        return new Day(
                date,
                Progress.of(records),
                records.stream()
                        .map(dose -> Dose.from(dose, currentTime))
                        .toList()
        );
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
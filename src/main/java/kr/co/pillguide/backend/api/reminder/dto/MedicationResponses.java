package kr.co.pillguide.backend.api.reminder.dto;

import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import kr.co.pillguide.backend.api.reminder.entity.MedicationReminder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

// 복약 기능의 API 응답 DTO를 모아둔 클래스
public final class MedicationResponses {

    // DTO를 묶는 용도이므로 외부에서 인스턴스를 생성하지 않음
    private MedicationResponses() {
    }

    // 복약 알림 설정 조회 응답
    public record Reminder(
            Long id,                       // 알림 ID
            String medicationName,         // 약 이름: 아스피린 등
            String dosage,                 // 1회 복용량: 1정, 1포 등
            LocalTime time,                // 매회 복약 예정 시각: 08:00 등
            LocalDate startDate,           // 복약 일정 시작일
            Set<DayOfWeek> repeatDays,      // 반복 요일. 빈 집합이면 시작일에 한 번만 예약
            String memo,                   // 복약 메모: 아침 식후 등
            boolean enabled                // 푸시 알림 활성화 여부. 꺼도 체크리스트는 유지
    ) {
        public static Reminder from(MedicationReminder reminder) {
            return new Reminder(
                    reminder.getId(),
                    reminder.getMedicationName(),
                    reminder.getDosage(),
                    reminder.getTime(),
                    reminder.getStartDate(),
                    Set.copyOf(reminder.getRepeatDays()),
                    reminder.getMemo(),
                    reminder.isEnabled()
            );
        }
    }

    // 특정 날짜의 개별 복약 기록 응답
    public record Dose(
            Long id,                       // 복약 기록 ID. 완료·취소 API 호출에 사용
            Long reminderId,               // 이 기록을 생성한 알림의 ID
            String medicationName,         // 기록 생성 당시의 약 이름
            String dosage,                 // 기록 생성 당시의 1회 복용량
            LocalDateTime scheduledAt,     // 해당 회차의 복약 예정 날짜·시각
            String memo,                   // 기록 생성 당시의 복약 메모
            String status,                 // TAKEN: 완료 / SCHEDULED: 예정 / MISSED: 시각 경과 후 미체크
            LocalDateTime takenAt          // 완료 체크 시각. 미완료 또는 완료 취소 상태이면 null
    ) {
        public static Dose from(
                MedicationDose dose,
                LocalDateTime now
        ) {
            String status;

            if (dose.getTakenAt() != null) {
                status = "TAKEN";
            } else if (dose.getScheduledAt().isBefore(now)) {
                // 예정 시각이 지났어도 이후에 완료 체크할 수 있음
                status = "MISSED";
            } else {
                status = "SCHEDULED";
            }

            return new Dose(
                    dose.getId(),
                    dose.getReminder().getId(),
                    dose.getMedicationName(),
                    dose.getDosage(),
                    dose.getScheduledAt(),
                    dose.getMemo(),
                    status,
                    dose.getTakenAt()
            );
        }
    }

    // 조회 대상 기록들의 복약 진행률
    public record Progress(
            int total,                     // 전체 복약 예정 건수
            int taken,                     // 완료 체크한 건수
            int remaining,                 // 미완료 건수: total - taken. 시각이 지난 미체크 기록도 포함
            int percentage                 // 완료 비율(%). 반올림한 정수이며 전체 0건이면 0
    ) {
        public static Progress of(List<MedicationDose> doses) {
            int total = doses.size();

            int taken = (int) doses.stream()
                    .filter(dose -> dose.getTakenAt() != null)
                    .count();

            int percentage = total == 0
                    ? 0
                    : (int) Math.round(taken * 100.0 / total);

            return new Progress(
                    total,
                    taken,
                    total - taken,
                    percentage
            );
        }
    }

    // 하루의 복약 체크리스트 응답
    public record Day(
            LocalDate date,                // 조회한 날짜
            Progress progress,             // 해당 날짜의 전체 건수·완료 건수·진행률
            List<Dose> doses               // 해당 날짜의 개별 복약 기록 목록
    ) {
    }

    // 주간 복약 기록 응답
    public record Week(
            LocalDate from,                // 조회 주의 시작일: 월요일
            LocalDate to,                  // 조회 주의 종료일: 일요일
            Progress progress,             // 해당 주 전체 진행률. 미래 예정 기록도 전체 건수에 포함
            List<Day> days                 // 월요일부터 일요일까지 7일의 날짜별 기록
    ) {
    }

    // 홈 화면 응답
    public record Home(
            LocalDate date,                // 한국 시간 기준 오늘 날짜
            Progress progress,             // 오늘의 복약 진행률
            int streak,                    // 복약 계획이 있고 모두 완료한 연속 날짜 수
            Dose nextDose,                 // 향후 7일 내 활성 알림 중 가장 가까운 미복용 일정. 없으면 null
            List<Dose> doses               // 오늘의 복약 체크리스트
    ) {
    }
}
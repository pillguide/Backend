package kr.co.pillguide.backend.api.reminder.repository;

import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MedicationDoseRepository
        extends JpaRepository<MedicationDose, Long> {

    // 회원의 날짜 범위별 복약 기록 조회
    List<MedicationDose>
    findAllByReminder_Member_IdAndDateBetweenOrderByScheduledAtAscIdAsc(
            Long memberId,
            LocalDate from,
            LocalDate to
    );

    // 본인 소유의 복약 기록만 조회
    Optional<MedicationDose>
    findByIdAndReminder_Member_Id(
            Long id,
            Long memberId
    );

    // 같은 알림·날짜의 기록 중복 생성 방지
    boolean existsByReminder_IdAndDate(
            Long reminderId,
            LocalDate date
    );

    // 알림 편집·삭제 시 정리할 미래 미복용 기록 조회
    List<MedicationDose>
    findAllByReminder_IdAndScheduledAtAfterAndTakenAtIsNull(
            Long reminderId,
            LocalDateTime now
    );
}
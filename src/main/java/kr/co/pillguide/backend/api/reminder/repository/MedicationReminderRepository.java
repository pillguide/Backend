package kr.co.pillguide.backend.api.reminder.repository;

import kr.co.pillguide.backend.api.reminder.entity.MedicationReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MedicationReminderRepository
        extends JpaRepository<MedicationReminder, Long> {

    // 로그인 회원의 삭제되지 않은 알림 목록
    List<MedicationReminder>
    findAllByMember_IdAndDeletedFalseOrderByTimeAscIdAsc(
            Long memberId
    );

    // 본인 소유의 알림만 조회
    Optional<MedicationReminder>
    findByIdAndMember_IdAndDeletedFalse(
            Long id,
            Long memberId
    );

    // FCM 전송 대상: 삭제되지 않은 활성 알림이 있는 회원
    @Query("""
            select distinct r.member.id
            from MedicationReminder r
            where r.deleted = false
              and r.enabled = true
            """)
    List<Long> findNotificationMemberIds();
}
package kr.co.pillguide.backend.api.reminder.service;

import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MedicationNotificationService {

    private final MedicationService medicationService;
    private final Clock clock;

    public MedicationNotificationService(
            MedicationService medicationService,
            @Qualifier("medicationClock") Clock clock
    ) {
        this.medicationService = medicationService;
        this.clock = clock;
    }

    @Transactional
    public List<NotificationTarget> prepare(Long memberId) {
        // 다른 서버에서도 같은 회원은 동시에 처리할 수 없도록 잠금
        Member member = medicationService.lockMember(memberId);

        String token = member.getFcmToken();

        if (token == null || token.isBlank()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = now.minusMinutes(5);

        // 자정 전후도 처리할 수 있도록 조회 범위를 날짜로 변환
        List<MedicationDose> records = medicationService.records(
                memberId,
                from.toLocalDate(),
                now.toLocalDate()
        );

        List<NotificationTarget> targets = new ArrayList<>();

        for (MedicationDose dose : records) {
            if (dose.getTakenAt() != null
                    || dose.getNotificationAttemptedAt() != null
                    || dose.getReminder().isDeleted()
                    || !dose.getReminder().isEnabled()
                    || dose.getScheduledAt().isBefore(from)
                    || dose.getScheduledAt().isAfter(now)) {
                continue;
            }

            // 트랜잭션 커밋 후 전송하므로 다음 실행에서 중복 선점되지 않음
            dose.notificationAttempted(now);

            targets.add(new NotificationTarget(
                    dose.getId(),
                    memberId,
                    token,
                    dose.getMedicationName()
            ));
        }

        return targets;
    }

    public record NotificationTarget(
            Long doseId,
            Long memberId,
            String token,
            String medicationName
    ) {
        // 토큰이 실수로 로그에 출력되지 않도록 제한
        @Override
        public String toString() {
            return "NotificationTarget[doseId=" + doseId + "]";
        }
    }
}
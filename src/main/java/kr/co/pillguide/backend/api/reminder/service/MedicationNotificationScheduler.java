package kr.co.pillguide.backend.api.reminder.service;

import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.reminder.repository.MedicationReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"fcm.enabled", "medication.notification.enabled"}, havingValue = "true")
public class MedicationNotificationScheduler {
    private final MedicationReminderRepository reminderRepository;
    private final MedicationNotificationService notificationService;
    private final FcmService fcmService;

    @Scheduled(fixedDelayString = "${medication.notification.poll-ms:30000}")
    public void sendDueNotifications() {
        List<Long> memberIds;
        try {
            memberIds = reminderRepository.findNotificationMemberIds();
            if (memberIds.isEmpty()) {
                return;
            }
            fcmService.refreshCredentials();
        } catch (Exception exception) {
            log.warn("복약 알림 준비 실패: errorType={}", exception.getClass().getSimpleName());
            return;
        }
        for (Long memberId : memberIds) {
            List<Long> doseIds;
            try {
                doseIds = notificationService.prepare(memberId);
            } catch (Exception exception) {
                log.warn("복약 일정 조회 실패: memberId={}, errorType={}", memberId, exception.getClass().getSimpleName());
                continue;
            }
            for (Long doseId : doseIds) {
                try {
                    notificationService.send(memberId, doseId);
                } catch (Exception exception) {
                    log.warn("복약 알림 처리 실패: doseId={}, errorType={}", doseId, exception.getClass().getSimpleName());
                }
            }
        }
    }
}

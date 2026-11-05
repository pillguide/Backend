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
@ConditionalOnProperty(
        name = {
                "fcm.enabled",
                "medication.notification.enabled"
        },
        havingValue = "true"
)
public class MedicationNotificationScheduler {

    private final MedicationReminderRepository reminderRepository;
    private final MedicationNotificationService notificationService;
    private final FcmService fcmService;

    @Scheduled(
            fixedDelayString = "${medication.notification.poll-ms:30000}"
    )
    public void sendDueNotifications() {
        log.info("복약 알림 스케줄러 실행");

        List<Long> memberIds;

        try {
            memberIds = reminderRepository.findNotificationMemberIds();

            log.info(
                    "복약 알림 대상 회원 수={}",
                    memberIds.size()
            );

        } catch (Exception exception) {
            log.error(
                    "복약 알림 대상 조회 실패: errorType={}",
                    exception.getClass().getSimpleName()
            );
            return;
        }

        for (Long memberId : memberIds) {
            List<MedicationNotificationService.NotificationTarget> targets;

            try {
                targets = notificationService.prepare(memberId);

                log.info(
                        "복약 발송 대상: memberId={}, count={}",
                        memberId,
                        targets.size()
                );

            } catch (Exception exception) {
                log.error(
                        "복약 알림 준비 실패: memberId={}, errorType={}",
                        memberId,
                        exception.getClass().getSimpleName()
                );
                continue;
            }

            for (var target : targets) {
                try {
                    log.info(
                            "복약 알림 전송 시도: doseId={}",
                            target.doseId()
                    );

                    FcmService.SendResult result =
                            fcmService.sendMedicationNotification(
                                    target.memberId(),
                                    target.token(),
                                    target.doseId(),
                                    target.medicationName()
                            );

                    if (result == FcmService.SendResult.ACCEPTED) {
                        log.info(
                                "복약 알림 FCM 접수 완료: doseId={}",
                                target.doseId()
                        );
                    } else {
                        log.warn(
                                "복약 알림 전송 실패: doseId={}, result={}",
                                target.doseId(),
                                result
                        );
                    }

                } catch (Exception exception) {
                    log.error(
                            "복약 알림 처리 오류: doseId={}, errorType={}",
                            target.doseId(),
                            exception.getClass().getSimpleName()
                    );
                }
            }
        }

        log.info("복약 알림 스케줄러 실행 완료");
    }
}
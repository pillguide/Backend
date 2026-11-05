package kr.co.pillguide.backend.api.member.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import kr.co.pillguide.backend.api.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class FcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final MemberRepository memberRepository;

    public enum SendResult {
        ACCEPTED,
        INVALID_TOKEN,
        FAILED
    }

    public SendResult sendMedicationNotification(
            Long memberId,
            String token,
            Long doseId,
            String medicationName
    ) {
        if (token == null || token.isBlank()) {
            return SendResult.INVALID_TOKEN;
        }

        Message message = Message.builder()
                .setToken(token)
                .setNotification(
                        Notification.builder()
                                .setTitle("복약 알림")
                                .setBody(
                                        medicationName + " 복용 시간이에요."
                                )
                                .build()
                )
                .putData("type", "MEDICATION_REMINDER")
                .putData("doseId", doseId.toString())
                .build();

        try {
            // FCM 접수 성공이며 기기 수신 완료를 의미하지 않습니다.
            firebaseMessaging.send(message);

            return SendResult.ACCEPTED;

        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode errorCode =
                    exception.getMessagingErrorCode();

            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                // 전송 중 새 토큰이 등록됐다면 새 토큰은 삭제하지 않습니다.
                memberRepository.clearFcmTokenIfMatches(
                        memberId,
                        token
                );

                return SendResult.INVALID_TOKEN;
            }

            // 토큰이나 SDK 예외 원문은 로그에 출력하지 않습니다.
            log.warn(
                    "FCM 전송 실패: doseId={}, errorCode={}",
                    doseId,
                    errorCode
            );

            return SendResult.FAILED;
        }
    }
}
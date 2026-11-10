package kr.co.pillguide.backend.api.reminder.service;

import jakarta.persistence.EntityManager;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.member.service.FcmService;
import kr.co.pillguide.backend.api.reminder.entity.MedicationDose;
import kr.co.pillguide.backend.api.reminder.repository.MedicationDoseRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicationNotificationService {
    private final MedicationService medicationService;
    private final MedicationDoseRepository doses;
    private final EntityManager em;
    private final ObjectProvider<FcmService> sender;
    private final Clock clock;

    public MedicationNotificationService(MedicationService medicationService,
            MedicationDoseRepository doses, EntityManager em, ObjectProvider<FcmService> sender,
            @Qualifier("medicationClock") Clock clock) {
        this.medicationService = medicationService;
        this.doses = doses;
        this.em = em;
        this.sender = sender;
        this.clock = clock;
    }

    @Transactional(timeout = 10)
    public List<Long> prepare(Long memberId) {
        Member member = medicationService.lockMember(memberId);
        if (member.getFcmToken() == null || member.getFcmToken().isBlank()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return medicationService.records(memberId, now.minusMinutes(5).toLocalDate(), now.toLocalDate())
                .stream().filter(dose -> dose.canNotify(now)).map(MedicationDose::getId).toList();
    }

    // The member lock orders dispatch against logout, token transfer and medication changes.
    @Transactional(timeout = 10)
    public void send(Long memberId, Long doseId) {
        Member member = medicationService.lockMember(memberId);
        var found = doses.findByIdAndReminder_Member_Id(doseId, memberId);
        FcmService fcm = sender.getIfAvailable();
        if (found.isEmpty() || fcm == null || member.getFcmToken() == null) {
            return;
        }
        MedicationDose dose = found.get();
        em.refresh(dose);
        em.refresh(dose.getReminder());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!dose.canNotify(now)) {
            return;
        }
        dose.notificationAttempted(now);
        var result = fcm.sendMedicationNotification(member.getFcmToken(), doseId, dose.getMedicationName());
        switch (result.outcome()) {
            case ACCEPTED -> dose.notificationAccepted(LocalDateTime.now(clock));
            case INVALID_TOKEN -> {
                member.clearFcmToken();
                dose.retryNotificationAfter(now.plusSeconds(30));
            }
            case PERMANENT_FAILURE -> dose.stopNotification(now);
            case RETRY -> {
                Duration delay = result.retryAfter();
                if (delay.compareTo(Duration.ofMinutes(5)) > 0) {
                    dose.retryNotificationAfter(dose.getScheduledAt().plusMinutes(5).plusSeconds(1));
                } else {
                    dose.retryNotificationAfter(LocalDateTime.now(clock).plus(delay));
                }
            }
        }
        em.flush();
    }
}

package kr.co.pillguide.backend.api.reminder.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "medication_dose",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dose_reminder_date",
                columnNames = {"reminder_id", "dose_date"}
        ),
        indexes = @Index(
                name = "idx_dose_date",
                columnList = "dose_date"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationDose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false)
    private MedicationReminder reminder;

    @Column(name = "dose_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false, length = 100)
    private String medicationName;

    @Column(nullable = false, length = 50)
    private String dosage;

    @Column(length = 500)
    private String memo;

    // null이면 미복용, 값이 있으면 복용 완료
    private LocalDateTime takenAt;

    // 동일 예약에 대한 반복 푸시 방지
    private LocalDateTime notificationAttemptedAt;

    public MedicationDose(
            MedicationReminder reminder,
            LocalDate date
    ) {
        this.reminder = reminder;
        this.date = date;
        this.scheduledAt = date.atTime(reminder.getTime());
        this.medicationName = reminder.getMedicationName();
        this.dosage = reminder.getDosage();
        this.memo = reminder.getMemo();
    }

    public void check(boolean taken, LocalDateTime now) {
        if (!taken) {
            this.takenAt = null;
            return;
        }

        // 완료 요청이 중복되어도 최초 완료 시각 유지
        if (this.takenAt == null) {
            this.takenAt = now;
        }
    }

    public void notificationAttempted(LocalDateTime now) {
        this.notificationAttemptedAt = now;
    }
}
package kr.co.pillguide.backend.api.reminder.entity;

import jakarta.persistence.*;
import kr.co.pillguide.backend.api.member.entity.Member;
import kr.co.pillguide.backend.api.reminder.dto.ReminderRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "medication_reminder",
        indexes = @Index(
                name = "idx_reminder_member",
                columnList = "member_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String medicationName;

    @Column(nullable = false, length = 50)
    private String dosage;

    @Column(nullable = false)
    private LocalTime time;

    @Column(nullable = false)
    private LocalDate startDate;

    @ElementCollection
    @CollectionTable(
            name = "medication_reminder_day",
            joinColumns = @JoinColumn(name = "reminder_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"reminder_id", "day_of_week"}
            )
    )
    @Column(name = "day_of_week", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Set<DayOfWeek> repeatDays = new HashSet<>();

    @Column(length = 500)
    private String memo;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean deleted;

    // 날짜별 복약 기록을 어디까지 생성했는지 저장
    @Column(nullable = false)
    private LocalDate generatedThrough;

    public MedicationReminder(
            Member member,
            ReminderRequest request
    ) {
        this.member = member;
        update(request);
        this.generatedThrough = startDate.minusDays(1);
    }

    public void update(ReminderRequest request) {
        this.medicationName = request.medicationName().trim();
        this.dosage = request.dosage().trim();
        this.time = request.time().withSecond(0).withNano(0);
        this.startDate = request.startDate();

        this.repeatDays.clear();
        this.repeatDays.addAll(request.repeatDays());

        this.memo = request.memo();
        this.enabled = request.enabled();
    }

    public boolean occursOn(LocalDate date) {
        if (deleted || date.isBefore(startDate)) {
            return false;
        }

        // 반복 없음: 시작일에 한 번만 복용
        if (repeatDays.isEmpty()) {
            return date.equals(startDate);
        }

        return repeatDays.contains(date.getDayOfWeek());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // 과거 복약 기록 보존을 위한 소프트 삭제
    public void delete() {
        this.deleted = true;
        this.enabled = false;
    }

    public void generatedThrough(LocalDate date) {
        this.generatedThrough = date;
    }
}
package kr.co.pillguide.backend.api.reminder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record ReminderRequest(

        @NotBlank
        @Size(max = 100)
        String medicationName,

        @NotBlank
        @Size(max = 50)
        String dosage,

        @NotNull
        LocalTime time,

        @NotNull
        LocalDate startDate,

        @NotNull
        Set<@NotNull DayOfWeek> repeatDays,

        @Size(max = 500)
        String memo,

        @NotNull
        Boolean enabled

) {
    public static class MedicationReminder {
    }
}
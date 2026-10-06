package kr.co.pillguide.backend.api.reminder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class MedicationTimeConfig {

    // 복약 일정·완료 시각·푸시 전송 시각 계산에 사용할 Clock
    @Bean("medicationClock")
    public Clock medicationClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
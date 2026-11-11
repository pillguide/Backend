package kr.co.pillguide.backend.api.member.service;

import kr.co.pillguide.backend.api.member.repository.LoginExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "app.oauth2.exchange-cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LoginExchangeCleanup {
    private final LoginExchangeRepository exchanges;

    @Scheduled(fixedDelayString = "${app.oauth2.exchange-cleanup-ms:60000}",
            initialDelayString = "${app.oauth2.exchange-cleanup-initial-delay-ms:60000}")
    @Transactional
    public void deleteExpired() {
        exchanges.deleteExpiredBefore(LocalDateTime.now());
    }
}

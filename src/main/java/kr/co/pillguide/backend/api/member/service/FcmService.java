package kr.co.pillguide.backend.api.member.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded FCM request. Retry scheduling belongs to the medication service. */
@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class FcmService {
    private final GoogleCredentials credentials;
    private final HttpClient client;
    private final URI endpoint;
    private final Gson gson = new Gson();
    private final AtomicBoolean refreshRequired = new AtomicBoolean();

    @Autowired
    public FcmService(ServiceAccountCredentials credentials) {
        this(credentials.createScoped("https://www.googleapis.com/auth/firebase.messaging"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                URI.create("https://fcm.googleapis.com/v1/projects/" + credentials.getProjectId() + "/messages:send"));
    }

    FcmService(GoogleCredentials credentials, HttpClient client, URI endpoint) {
        this.credentials = credentials;
        this.client = client;
        this.endpoint = endpoint;
    }

    // Refresh outside the database transaction because credentials may require network access.
    public void refreshCredentials() throws IOException {
        if (refreshRequired.getAndSet(false)) {
            try {
                credentials.refresh();
            } catch (IOException exception) {
                refreshRequired.set(true);
                throw exception;
            }
        } else {
            credentials.refreshIfExpired();
        }
    }

    public SendResult sendMedicationNotification(String token, Long doseId, String medicationName) {
        var accessToken = credentials.getAccessToken();
        if (accessToken == null || accessToken.getExpirationTime().toInstant().isBefore(Instant.now())) {
            return new SendResult(Outcome.RETRY, Duration.ofSeconds(30));
        }
        String payload = gson.toJson(Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", "복약 알림", "body", medicationName + " 복용 시간이에요."),
                "data", Map.of("type", "MEDICATION_REMINDER", "doseId", doseId.toString()))));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + accessToken.getTokenValue())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            // HttpRequest.timeout alone does not bound a stalled response body.
            var response = pending.get(5, TimeUnit.SECONDS);
            if (response.statusCode() == 200) {
                return new SendResult(Outcome.ACCEPTED, Duration.ZERO);
            }
            if (isUnregistered(response.body())) {
                return new SendResult(Outcome.INVALID_TOKEN, Duration.ZERO);
            }
            if (response.statusCode() == 408 || response.statusCode() == 429
                    || response.statusCode() >= 500 || response.statusCode() == 401) {
                if (response.statusCode() == 401) {
                    refreshRequired.set(true);
                }
                return new SendResult(Outcome.RETRY,
                        retryDelay(response.headers().firstValue("Retry-After").orElse("")));
            }
            return new SendResult(Outcome.PERMANENT_FAILURE, Duration.ZERO);
        } catch (ExecutionException | TimeoutException exception) {
            pending.cancel(true);
            return new SendResult(Outcome.RETRY, Duration.ofSeconds(30));
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            return new SendResult(Outcome.RETRY, Duration.ofSeconds(30));
        }
    }

    private boolean isUnregistered(String body) {
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            var error = root.getAsJsonObject("error");
            if (error == null || !error.has("details")) {
                return false;
            }
            for (var detail : error.getAsJsonArray("details")) {
                var object = detail.getAsJsonObject();
                if (object.has("errorCode") && "UNREGISTERED".equals(object.get("errorCode").getAsString())) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // Error bodies are untrusted and may contain registration tokens.
        }
        return false;
    }

    private Duration retryDelay(String header) {
        Duration minimum = Duration.ofSeconds(60);
        try {
            Duration requested = Duration.ofSeconds(Long.parseLong(header));
            return requested.compareTo(minimum) > 0 ? requested : minimum;
        } catch (NumberFormatException ignored) {
            try {
                Duration requested = Duration.between(Instant.now(),
                        ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return requested.compareTo(minimum) > 0 ? requested : minimum;
            } catch (RuntimeException invalidHeader) {
                return minimum;
            }
        }
    }

    public enum Outcome { ACCEPTED, INVALID_TOKEN, RETRY, PERMANENT_FAILURE }

    public record SendResult(Outcome outcome, Duration retryAfter) { }
}

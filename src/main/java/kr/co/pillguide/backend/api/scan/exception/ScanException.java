package kr.co.pillguide.backend.api.scan.exception;

import org.springframework.http.HttpStatus;

/** Safe user-facing scan failure, without an upstream cause or response body. */
public final class ScanException extends RuntimeException {
    private final HttpStatus status;
    private final Integer retryAfterSeconds;

    public ScanException(HttpStatus status, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() { return status; }
    public HttpStatus getStatus() { return status; }
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}

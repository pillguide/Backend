package kr.co.pillguide.backend.api.drug.reference;

/** Contains only a stable code and HTTP status. Never retain raw response or request details. */
public final class MfdsFetchException extends RuntimeException {
    private final String code;
    private final int status;

    public MfdsFetchException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}

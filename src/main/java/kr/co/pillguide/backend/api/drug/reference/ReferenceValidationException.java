package kr.co.pillguide.backend.api.drug.reference;

/** A stable, credential-free reason to retain the last good reference batch. */
public class ReferenceValidationException extends RuntimeException {
    private final String code;
    private final String detail;

    public ReferenceValidationException(String code, String detail) {
        super(code + ": " + detail);
        this.code = code;
        this.detail = detail;
    }

    public String code() { return code; }
    public String detail() { return detail; }
}

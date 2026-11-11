package kr.co.pillguide.backend.api.drug.reference;

public final class ReferenceSyncException extends RuntimeException {
    public ReferenceSyncException(String code) { super(code); }
    public String code() { return getMessage(); }
}

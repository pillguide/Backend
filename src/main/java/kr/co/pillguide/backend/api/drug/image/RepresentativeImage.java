package kr.co.pillguide.backend.api.drug.image;

/** Current image delivery metadata; never persisted in a scan snapshot. */
public record RepresentativeImage(String url, String status) {
    public static RepresentativeImage notReady() {
        return new RepresentativeImage(null, "NOT_READY");
    }
}

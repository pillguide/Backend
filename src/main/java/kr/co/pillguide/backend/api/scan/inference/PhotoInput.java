package kr.co.pillguide.backend.api.scan.inference;

/** Validated in-memory image. Width and height are after EXIF orientation. */
public record PhotoInput(byte[] bytes, String mediaType, String sha256, int width, int height) {
    public PhotoInput {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() { return bytes.clone(); }
}

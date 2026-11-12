package kr.co.pillguide.backend.api.scan.inference;

import kr.co.pillguide.backend.api.scan.exception.ScanException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoValidatorTest {
    private final PhotoValidator validator = new PhotoValidator();

    @Test
    void sharedHdrStructureCasesRejectMalformedAndUnrelatedMultiImages() throws Exception {
        try (var cases = getClass().getResourceAsStream("/images/cases.tsv")) {
            for (String line : new String(cases.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                String[] parts = line.split("\t");
                byte[] data;
                try (var resource = getClass().getResourceAsStream("/images/" + parts[0] + ".b64")) {
                    data = java.util.Base64.getMimeDecoder().decode(resource.readAllBytes());
                }
                if (parts[1].equals("415")) {
                    assertThatThrownBy(() -> validator.validate(file("image/jpeg", data)))
                            .as(parts[0]).isInstanceOf(ScanException.class)
                            .satisfies(e -> assertThat(((ScanException) e).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
                } else {
                    PhotoInput input = validator.validate(file("image/jpeg", data));
                    assertThat(input.width()).as(parts[0]).isEqualTo(24);
                    assertThat(input.height()).as(parts[0]).isEqualTo(32);
                }
            }
        }
    }

    @Test
    void allStandardPhoneExifOrientationsAgreeForJpegAndPng() throws Exception {
        for (int orientation = 1; orientation <= 8; orientation++) {
            byte[][] images = {jpegWithOrientation(image("jpeg", 32, 24), orientation),
                    insertPngChunk(image("png", 32, 24), "eXIf", tiffOrientation(orientation))};
            for (int i = 0; i < images.length; i++) {
                PhotoInput input = validator.validate(file(i == 0 ? "image/jpeg" : "image/png", images[i]));
                assertThat(input.width()).isEqualTo(orientation >= 5 ? 24 : 32);
                assertThat(input.height()).isEqualTo(orientation >= 5 ? 32 : 24);
            }
        }
    }

    @Test
    void acceptsHdrAuxiliaryJpegAndPreservesOriginalHashAndPrimaryOrientation() throws Exception {
        byte[] jpeg;
        try (var resource = getClass().getResourceAsStream("/images/apple-hdr.jpg.b64")) {
            jpeg = java.util.Base64.getMimeDecoder().decode(resource.readAllBytes());
        }
        PhotoInput input = validator.validate(file("image/jpeg", jpeg));
        assertThat(input.width()).isEqualTo(24);
        assertThat(input.height()).isEqualTo(32);
        assertThat(input.bytes()).isEqualTo(jpeg);
        assertThat(input.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg)));
    }

    @Test
    void acceptsPngInMemoryAndHashesOriginalBytes() throws Exception {
        byte[] png = image("png", 3, 2);
        PhotoInput input = validator.validate(file("image/png", png));
        assertThat(input.mediaType()).isEqualTo("image/png");
        assertThat(input.width()).isEqualTo(3);
        assertThat(input.height()).isEqualTo(2);
        assertThat(input.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)));
        input.bytes()[0] = 0;
        assertThat(input.bytes()).isEqualTo(png);
    }

    @Test
    void transposesExifOrientedJpegDimensions() throws Exception {
        byte[] jpeg = jpegWithOrientation(image("jpeg", 2, 3), 6);
        PhotoInput input = validator.validate(file("image/jpeg", jpeg));
        assertThat(input.width()).isEqualTo(3);
        assertThat(input.height()).isEqualTo(2);
    }

    @Test
    void transposesPngExifAndSkipsJpegMarkerFillBytes() throws Exception {
        byte[] png = insertPngChunk(image("png", 2, 3), "eXIf", tiffOrientation(6));
        PhotoInput orientedPng = validator.validate(file("image/png", png));
        assertThat(orientedPng.width()).isEqualTo(3);
        assertThat(orientedPng.height()).isEqualTo(2);

        byte[] jpeg = jpegWithOrientation(image("jpeg", 2, 3), 6);
        byte[] filled = new byte[jpeg.length + 1];
        System.arraycopy(jpeg, 0, filled, 0, 3);
        filled[3] = (byte) 0xff;
        System.arraycopy(jpeg, 3, filled, 4, jpeg.length - 3);
        PhotoInput orientedJpeg = validator.validate(file("image/jpeg", filled));
        assertThat(orientedJpeg.width()).isEqualTo(3);
        assertThat(orientedJpeg.height()).isEqualTo(2);
    }

    @Test
    void rejectsWrongMediaMagicTruncationAnimationAndOversizedPixels() throws Exception {
        byte[] png = image("png", 3, 2);
        assertStatus(file("image/jpeg", png), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(file("image/png", new byte[] {(byte) 0x89, 0x50, 0x4e}), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        byte[] jpeg = image("jpeg", 3, 2);
        assertStatus(file("image/jpeg", java.util.Arrays.copyOf(jpeg, jpeg.length - 2)), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(file("image/png", insertPngChunk(png, "acTL", new byte[8])), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        byte[] hugeHeader = png.clone();
        ByteBuffer.wrap(hugeHeader, 16, 8).putInt(5000).putInt(5000);
        CRC32 crc = new CRC32();
        crc.update(hugeHeader, 12, 17);
        ByteBuffer.wrap(hugeHeader, 29, 4).putInt((int) crc.getValue());
        assertStatus(file("image/png", hugeHeader), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsOversizedFileBeforeReadingBytes() {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        assertStatus(file("image/png", bytes), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private void assertStatus(MockMultipartFile file, HttpStatus status) {
        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ScanException.class)
                .satisfies(error -> assertThat(((ScanException) error).status()).isEqualTo(status));
    }

    private static MockMultipartFile file(String type, byte[] bytes) {
        return new MockMultipartFile("file", "private-name", type, bytes);
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }

    private static byte[] jpegWithOrientation(byte[] jpeg, int orientation) throws Exception {
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write(new byte[] {'E','x','i','f',0,0});
        exif.write(tiffOrientation(orientation));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(jpeg, 0, 2);
        result.write(0xff); result.write(0xe1);
        result.write((exif.size() + 2) >> 8); result.write((exif.size() + 2) & 0xff);
        result.write(exif.toByteArray());
        result.write(jpeg, 2, jpeg.length - 2);
        return result.toByteArray();
    }

    private static byte[] tiffOrientation(int orientation) {
        return new byte[] {'I','I',42,0,8,0,0,0,1,0,
                0x12,0x01,3,0,1,0,0,0,(byte) orientation,0,0,0,0,0,0,0};
    }

    private static byte[] insertPngChunk(byte[] png, String type, byte[] data) throws Exception {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name); crc.update(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(name); out.write(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }
}

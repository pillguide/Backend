package kr.co.pillguide.backend.api.scan.inference;

import kr.co.pillguide.backend.api.scan.exception.ScanException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.zip.CRC32;

@Component
public final class PhotoValidator {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    public PhotoInput validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "사진 파일이 필요합니다.");
        if (file.getSize() > MAX_BYTES) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "사진 크기 제한을 초과했습니다.");
        String declaredType = file.getContentType();
        if (!"image/jpeg".equals(declaredType) && !"image/png".equals(declaredType)) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG 또는 PNG 사진만 사용할 수 있습니다.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "사진을 읽을 수 없습니다.");
        }
        if (bytes.length == 0) throw error(HttpStatus.BAD_REQUEST, "사진 파일이 필요합니다.");
        if (bytes.length > MAX_BYTES) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "사진 크기 제한을 초과했습니다.");
        boolean jpeg = isJpeg(bytes);
        boolean png = isPng(bytes);
        if ((!jpeg && !png) || (jpeg && !"image/jpeg".equals(declaredType))
                || (png && !"image/png".equals(declaredType))) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "사진 형식이 올바르지 않습니다.");
        }
        if (jpeg && (bytes.length < 4 || (bytes[bytes.length - 2] & 0xff) != 0xff
                || (bytes[bytes.length - 1] & 0xff) != 0xd9)) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        }
        int orientation = jpeg ? jpegExifOrientation(bytes) : pngOrientation(bytes);

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int rawWidth = reader.getWidth(0);
                int rawHeight = reader.getHeight(0);
                if (rawWidth <= 0 || rawHeight <= 0) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
                if ((long) rawWidth * rawHeight > MAX_PIXELS) {
                    throw error(HttpStatus.PAYLOAD_TOO_LARGE, "사진 픽셀 제한을 초과했습니다.");
                }
                int images = reader.getNumImages(true);
                if (images > 1 || (jpeg && HdrJpeg.hasMpIndex(bytes))) {
                    if (!jpeg || images != 2) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "움직이는 사진은 사용할 수 없습니다.");
                    HdrJpeg.validate(bytes);
                    int auxiliaryWidth = reader.getWidth(1), auxiliaryHeight = reader.getHeight(1);
                    if ((long) auxiliaryWidth * auxiliaryHeight > MAX_PIXELS) {
                        throw error(HttpStatus.PAYLOAD_TOO_LARGE, "사진 픽셀 제한을 초과했습니다.");
                    }
                    if (auxiliaryWidth <= 0 || auxiliaryHeight <= 0
                            || auxiliaryWidth > rawWidth || auxiliaryHeight > rawHeight) {
                        throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 HDR 사진입니다.");
                    }
                    var auxiliary = reader.read(1);
                    if (auxiliary == null || auxiliary.getRaster().getNumBands() != 1) {
                        throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 HDR 사진입니다.");
                    }
                    auxiliary.flush();
                }
                // Decode only after the pixel cap so damaged compressed data is rejected without unbounded allocation.
                if (reader.read(0) == null) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
                int width = orientation >= 5 && orientation <= 8 ? rawHeight : rawWidth;
                int height = orientation >= 5 && orientation <= 8 ? rawWidth : rawHeight;
                return new PhotoInput(bytes, declaredType, sha256(bytes), width, height);
            } finally {
                reader.dispose();
            }
        } catch (ScanException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        }
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }

    private static boolean isPng(byte[] b) {
        if (b.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (b[i] != PNG_SIGNATURE[i]) return false;
        return true;
    }

    private static int pngOrientation(byte[] bytes) {
        int offset = PNG_SIGNATURE.length;
        boolean idat = false;
        boolean end = false;
        boolean first = true;
        int orientation = 1;
        boolean sawExif = false;
        while (offset + 12 <= bytes.length) {
            long length = uint32(bytes, offset);
            if (length > bytes.length - (long) offset - 12) break;
            int data = offset + 8;
            int crcAt = data + (int) length;
            String type = new String(bytes, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (first && (!"IHDR".equals(type) || length != 13)) break;
            first = false;
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, 4 + (int) length);
            if (crc.getValue() != uint32(bytes, crcAt)) break;
            if ("acTL".equals(type)) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "움직이는 사진은 사용할 수 없습니다.");
            if ("eXIf".equals(type)) {
                if (sawExif) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
                sawExif = true;
                int tiff = length >= 6 && startsExif(bytes, data) ? data + 6 : data;
                orientation = tiffOrientation(bytes, tiff, crcAt);
            }
            if ("IDAT".equals(type)) idat = true;
            offset = crcAt + 4;
            if ("IEND".equals(type)) {
                end = length == 0 && offset == bytes.length;
                break;
            }
        }
        if (!idat || !end) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        return orientation;
    }

    private static int jpegExifOrientation(byte[] b) {
        int offset = 2;
        while (offset < b.length && (b[offset] & 0xff) == 0xff) {
            while (offset < b.length && (b[offset] & 0xff) == 0xff) offset++;
            if (offset >= b.length) break;
            int marker = b[offset++] & 0xff;
            if (marker == 0xda || marker == 0xd9) break;
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
            if (offset + 2 > b.length) break;
            int length = uint16be(b, offset);
            if (length < 2 || offset + (long) length > b.length) break;
            int payload = offset + 2;
            if (marker == 0xe1 && length >= 16 && startsExif(b, payload)) {
                return tiffOrientation(b, payload + 6, offset + length);
            }
            offset += length;
        }
        return 1;
    }

    private static boolean startsExif(byte[] b, int offset) {
        return b[offset] == 'E' && b[offset + 1] == 'x' && b[offset + 2] == 'i'
                && b[offset + 3] == 'f' && b[offset + 4] == 0 && b[offset + 5] == 0;
    }

    private static int tiffOrientation(byte[] b, int start, int end) {
        if (start < 0 || end > b.length || start + 8 > end)
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        boolean little = b[start] == 'I' && b[start + 1] == 'I';
        boolean big = b[start] == 'M' && b[start + 1] == 'M';
        if ((!little && !big) || uint16(b, start + 2, little) != 42) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        long ifdOffset = uint32(b, start + 4, little);
        long ifd = start + ifdOffset;
        if (ifd < start || ifd + 2 > end) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        int entries = uint16(b, (int) ifd, little);
        if (ifd + 2L + 12L * entries > end) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
        for (int i = 0; i < entries; i++) {
            int entry = (int) ifd + 2 + i * 12;
            if (uint16(b, entry, little) == 0x0112) {
                if (uint16(b, entry + 2, little) != 3 || uint32(b, entry + 4, little) != 1)
                    throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
                int orientation = uint16(b, entry + 8, little);
                if (orientation >= 1 && orientation <= 8) return orientation;
                throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 사진입니다.");
            }
        }
        return 1;
    }

    private static int uint16be(byte[] b, int offset) {
        return ((b[offset] & 0xff) << 8) | (b[offset + 1] & 0xff);
    }

    private static int uint16(byte[] b, int offset, boolean little) {
        return little ? (b[offset] & 0xff) | ((b[offset + 1] & 0xff) << 8) : uint16be(b, offset);
    }

    private static long uint32(byte[] b, int offset) { return uint32(b, offset, false); }

    private static long uint32(byte[] b, int offset, boolean little) {
        return little
                ? ((long) (b[offset] & 0xff) | ((long) (b[offset + 1] & 0xff) << 8)
                | ((long) (b[offset + 2] & 0xff) << 16) | ((long) (b[offset + 3] & 0xff) << 24))
                : (((long) (b[offset] & 0xff) << 24) | ((long) (b[offset + 1] & 0xff) << 16)
                | ((long) (b[offset + 2] & 0xff) << 8) | (b[offset + 3] & 0xff));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static ScanException error(HttpStatus status, String message) {
        return new ScanException(status, message, null);
    }
}

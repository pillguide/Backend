package kr.co.pillguide.backend.api.scan.inference;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

/** Strict two-image Apple HDR JPEG profile; arbitrary MPO remains unsupported. */
final class HdrJpeg {
    private static final String XMP = "http://ns.adobe.com/xap/1.0/\0";
    private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String AUX = "http://ns.apple.com/pixeldatainfo/1.0/";
    private static final String HDR = "http://ns.apple.com/HDRGainMap/1.0/";

    private HdrJpeg() {}

    static boolean hasMpIndex(byte[] bytes) {
        return segments(bytes, 0, bytes.length, true).stream()
                .anyMatch(s -> s.marker == 0xe2 && starts(bytes, s, "MPF\0"));
    }

    static void validate(byte[] bytes) {
        var headers = segments(bytes, 0, bytes.length, true);
        var indices = headers.stream().filter(s -> s.marker == 0xe2 && starts(bytes, s, "MPF\0")).toList();
        require(indices.size() == 1);
        Segment index = indices.getFirst();
        int base = index.start + 4;
        require(base + 8 <= index.end);
        boolean little = bytes[base] == 'I' && bytes[base + 1] == 'I';
        require(little || (bytes[base] == 'M' && bytes[base + 1] == 'M'));
        var tiff = new Tiff(bytes, base, index.end, little);
        require(tiff.u16(base + 2) == 42);
        long directory = base + tiff.u32(base + 4);
        require(directory >= base + 8);
        int count = tiff.u16(directory);
        var tags = new HashMap<Integer, Tag>();
        for (int i = 0; i < count; i++) {
            long pos = directory + 2 + i * 12L;
            int tag = tiff.u16(pos);
            require(tags.put(tag, new Tag(tiff.u16(pos + 2), tiff.u32(pos + 4), tiff.u32(pos + 8), (int) pos)) == null);
        }
        long directoryEnd = directory + 2 + count * 12L;
        require(tiff.u32(directoryEnd) == 0);
        Tag version = tags.get(0xb000), images = tags.get(0xb001), entries = tags.get(0xb002);
        require(version != null && version.kind == 7 && version.count == 4);
        require(new String(bytes, version.position + 8, 4, StandardCharsets.US_ASCII).equals("0100"));
        require(images != null && images.kind == 4 && images.count == 1 && images.value == 2);
        require(entries != null && entries.kind == 7 && entries.count == 32);
        long entryStart = base + entries.value;
        require(entryStart >= directoryEnd + 4);
        int[][] spans = new int[2][2];
        for (int i = 0; i < 2; i++) {
            long pos = entryStart + i * 16L;
            long attr = tiff.u32(pos), size = tiff.u32(pos + 4), offset = tiff.u32(pos + 8);
            require((i == 0 ? attr == 0x030000 || attr == 0x20030000 : attr == 0) && tiff.u32(pos + 12) == 0);
            require(i != 0 || offset == 0);
            long start = i == 0 ? 0 : base + offset;
            require(size >= 4 && start + size <= bytes.length);
            spans[i] = new int[]{(int) start, (int) (start + size)};
        }
        require(spans[0][1] == spans[1][0] && spans[1][1] == bytes.length);
        var primary = segments(bytes, 0, spans[0][1], false);
        require(primary.stream().filter(s -> s.marker == 0xe2 && starts(bytes, s, "MPF\0")).count() == 1);
        var auxiliary = segments(bytes, spans[1][0], spans[1][1], false);
        require(auxiliary.stream().noneMatch(s -> s.marker == 0xe2 && starts(bytes, s, "MPF\0")));
        var xmps = auxiliary.stream().filter(s -> s.marker == 0xe1 && starts(bytes, s, XMP)).toList();
        require(xmps.size() == 1 && appleGainMap(bytes, xmps.getFirst()));
    }

    private static boolean appleGainMap(byte[] bytes, Segment segment) {
        try {
            int start = segment.start + XMP.length();
            // Decode UTF-8 only; do not allow a UTF-16 document to bypass the declaration guard.
            String xml = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, start, segment.end - start)).toString();
            String upper = xml.toUpperCase(java.util.Locale.ROOT);
            if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) return false;
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var descriptions = document.getElementsByTagNameNS(RDF, "Description");
            for (int i = 0; i < descriptions.getLength(); i++) {
                var description = (Element) descriptions.item(i);
                if ("urn:com:apple:photo:2020:aux:hdrgainmap".equals(property(description, AUX, "AuxiliaryImageType"))
                        && "131072".equals(property(description, HDR, "HDRGainMapVersion"))) return true;
            }
        } catch (Exception ignored) { return false; }
        return false;
    }

    private static String property(Element element, String namespace, String name) {
        String value = element.hasAttributeNS(namespace, name) ? element.getAttributeNS(namespace, name) : null;
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e && namespace.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())) {
                require(value == null && e.getElementsByTagName("*").getLength() == 0);
                value = e.getTextContent().trim();
            }
        }
        return value;
    }

    private static List<Segment> segments(byte[] bytes, int start, int end, boolean headerOnly) {
        require(start + 2 <= end && (bytes[start] & 255) == 255 && (bytes[start + 1] & 255) == 0xd8);
        var result = new ArrayList<Segment>();
        int pos = start + 2;
        boolean entropy = false;
        boolean scanData = false;
        while (pos < end) {
            if (entropy) while (pos < end && (bytes[pos] & 255) != 255) { scanData = true; pos++; }
            require(pos < end && (bytes[pos] & 255) == 255);
            while (pos < end && (bytes[pos] & 255) == 255) pos++;
            require(pos < end);
            int marker = bytes[pos++] & 255;
            if (entropy && (marker == 0 || (marker >= 0xd0 && marker <= 0xd7))) {
                if (marker == 0) scanData = true;
                continue;
            }
            require(!entropy || scanData);
            entropy = false;
            if (marker == 0xd9) { require(pos == end); return result; }
            if (marker == 1) continue;
            require(marker != 0 && marker != 0xd8 && !(marker >= 0xd0 && marker <= 0xd7) && pos + 2 <= end);
            int length = ((bytes[pos] & 255) << 8) | (bytes[pos + 1] & 255);
            require(length >= 2 && pos + (long) length <= end);
            result.add(new Segment(marker, pos + 2, pos + length));
            pos += length;
            if (marker == 0xda) {
                if (headerOnly) return result;
                entropy = true;
                scanData = false;
            }
        }
        throw new IllegalArgumentException("Missing JPEG end");
    }

    private static boolean starts(byte[] bytes, Segment segment, String prefix) {
        if (segment.end - segment.start < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) if ((bytes[segment.start + i] & 255) != prefix.charAt(i)) return false;
        return true;
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid HDR JPEG structure");
    }

    private record Segment(int marker, int start, int end) {}
    private record Tag(int kind, long count, long value, int position) {}
    private record Tiff(byte[] bytes, int base, int end, boolean little) {
        private ByteBuffer at(long pos, int length) {
            require(pos >= base && pos + length <= end);
            return ByteBuffer.wrap(bytes, (int) pos, length).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        }
        int u16(long pos) { return Short.toUnsignedInt(at(pos, 2).getShort()); }
        long u32(long pos) { return Integer.toUnsignedLong(at(pos, 4).getInt()); }
    }
}

package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * ImageMetadataStripper는 사용자 이미지 업로드를 저장하기 전에 위치/기기/작성자 같은 메타데이터 컨테이너를 제거한다.
 * ImageMetadataStripper removes metadata containers that may expose location, device, or author information before user image uploads are stored.
 *
 * 픽셀 payload는 재인코딩하지 않고 JPEG/PNG/WebP 컨테이너만 보존적으로 재작성하며, 형식 파싱 실패 시 원본 손상을 피해야 한다.
 * Preserve image payloads without recompression, rewrite only recognized JPEG/PNG/WebP containers conservatively, and avoid corrupting malformed inputs.
 */

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Losslessly removes privacy-sensitive metadata containers from uploaded images.
 * Pixel/image payloads are not decoded or recompressed.
 */
public final class ImageMetadataStripper {
    private ImageMetadataStripper() {}

    public static byte[] stripForUpload(byte[] data, String extension) {
        if (data == null || data.length == 0) return data == null ? new byte[0] : data;
        String ext = String.valueOf(extension == null ? "" : extension).trim().toLowerCase(Locale.ROOT);
        try {
            return switch (ext) {
                case "jpg", "jpeg" -> stripJpeg(data);
                case "png" -> stripPng(data);
                case "webp" -> stripWebp(data);
                default -> data;
            };
        } catch (RuntimeException malformed) {
            // Upload validation historically allowed extension-based image handling.
            // Preserve that compatibility for malformed/unknown payloads rather than
            // corrupting bytes while attempting metadata removal.
            return data;
        }
    }

    private static byte[] stripJpeg(byte[] data) {
        if (data.length < 4 || u8(data[0]) != 0xFF || u8(data[1]) != 0xD8) return data;
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 2);
        int p = 2;
        boolean changed = false;
        while (p < data.length) {
            if (u8(data[p]) != 0xFF) {
                out.write(data, p, data.length - p);
                break;
            }
            int markerStart = p;
            while (p < data.length && u8(data[p]) == 0xFF) p++;
            if (p >= data.length) { out.write(data, markerStart, data.length - markerStart); break; }
            int marker = u8(data[p++]);
            // Start of scan: compressed image data follows; copy verbatim.
            if (marker == 0xDA) {
                if (p + 2 > data.length) return data;
                int len = be16(data, p);
                if (len < 2 || p + len > data.length) return data;
                out.write(data, markerStart, data.length - markerStart);
                break;
            }
            // Standalone markers have no length payload.
            if (marker == 0xD8 || marker == 0xD9 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(data, markerStart, p - markerStart);
                if (marker == 0xD9 && p < data.length) out.write(data, p, data.length - p);
                continue;
            }
            if (p + 2 > data.length) return data;
            int len = be16(data, p);
            if (len < 2 || p + len > data.length) return data;
            int segmentEnd = p + len;
            // APP1 carries EXIF/XMP; APP13 carries IPTC; COM may contain user/device metadata.
            boolean drop = marker == 0xE1 || marker == 0xED || marker == 0xFE;
            if (drop) changed = true;
            else out.write(data, markerStart, segmentEnd - markerStart);
            p = segmentEnd;
        }
        return changed ? out.toByteArray() : data;
    }

    private static byte[] stripPng(byte[] data) {
        byte[] sig = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (data.length < 12 || !Arrays.equals(Arrays.copyOf(data, 8), sig)) return data;
        Set<String> dropTypes = Set.of("eXIf", "tEXt", "zTXt", "iTXt");
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 8);
        int p = 8;
        boolean changed = false;
        while (p + 12 <= data.length) {
            long lenLong = be32u(data, p);
            if (lenLong > Integer.MAX_VALUE) return data;
            int len = (int) lenLong;
            int chunkEnd = p + 12 + len;
            if (len < 0 || chunkEnd < p || chunkEnd > data.length) return data;
            String type = ascii4(data, p + 4);
            if (dropTypes.contains(type)) changed = true;
            else out.write(data, p, chunkEnd - p);
            p = chunkEnd;
            if ("IEND".equals(type)) break;
        }
        if (p != data.length) {
            // Preserve valid trailing bytes only if parsing reached IEND exactly; malformed
            // input is returned untouched to avoid accidental corruption.
            if (p < data.length) return data;
        }
        return changed ? out.toByteArray() : data;
    }

    private static byte[] stripWebp(byte[] data) {
        if (data.length < 12 || !"RIFF".equals(ascii4(data, 0)) || !"WEBP".equals(ascii4(data, 8))) return data;
        ByteArrayOutputStream chunks = new ByteArrayOutputStream(data.length);
        int p = 12;
        boolean changed = false;
        while (p + 8 <= data.length) {
            String type = ascii4(data, p);
            long sizeLong = le32u(data, p + 4);
            if (sizeLong > Integer.MAX_VALUE) return data;
            int size = (int) sizeLong;
            int padded = size + (size & 1);
            int end = p + 8 + padded;
            if (size < 0 || end < p || end > data.length) return data;
            if ("EXIF".equals(type) || "XMP ".equals(type)) {
                changed = true;
            } else if ("VP8X".equals(type) && size >= 1) {
                byte[] chunk = Arrays.copyOfRange(data, p, end);
                int flagsAt = 8;
                int oldFlags = u8(chunk[flagsAt]);
                int newFlags = oldFlags & ~0x0C; // clear EXIF + XMP feature flags
                if (newFlags != oldFlags) { chunk[flagsAt] = (byte)newFlags; changed = true; }
                chunks.write(chunk, 0, chunk.length);
            } else {
                chunks.write(data, p, end - p);
            }
            p = end;
        }
        if (p != data.length || !changed) return data;
        byte[] body = chunks.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 12);
        out.write('R'); out.write('I'); out.write('F'); out.write('F');
        writeLe32(out, body.length + 4L);
        out.write('W'); out.write('E'); out.write('B'); out.write('P');
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static int u8(byte b) { return b & 0xFF; }
    private static int be16(byte[] b, int p) { return (u8(b[p]) << 8) | u8(b[p + 1]); }
    private static long be32u(byte[] b, int p) {
        return ((long)u8(b[p]) << 24) | ((long)u8(b[p+1]) << 16) | ((long)u8(b[p+2]) << 8) | u8(b[p+3]);
    }
    private static long le32u(byte[] b, int p) {
        return u8(b[p]) | ((long)u8(b[p+1]) << 8) | ((long)u8(b[p+2]) << 16) | ((long)u8(b[p+3]) << 24);
    }
    private static String ascii4(byte[] b, int p) {
        if (p < 0 || p + 4 > b.length) return "";
        return new String(new byte[]{b[p], b[p+1], b[p+2], b[p+3]}, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
    private static void writeLe32(ByteArrayOutputStream out, long v) {
        out.write((int)(v & 0xFF));
        out.write((int)((v >>> 8) & 0xFF));
        out.write((int)((v >>> 16) & 0xFF));
        out.write((int)((v >>> 24) & 0xFF));
    }
}

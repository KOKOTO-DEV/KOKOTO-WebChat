package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * 이미지 업로드 메타데이터 제거기의 컨테이너별 회귀 계약을 독립적으로 검증한다.
 * Independently validates per-container regression contracts for upload-time image metadata removal.
 */

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Regression coverage for upload-time image metadata removal. */
public final class ImageMetadataStripperHarness {
    private static int checks;

    public static void main(String[] args) {
        testJpeg();
        testPng();
        testWebp();
        testUntouchedFormats();
        System.out.println("IMAGE_METADATA_STRIPPER_PASS assertions=" + checks);
    }

    private static void testJpeg() {
        byte[] jpeg = bytes(
            0xFF,0xD8,
            0xFF,0xE0,0x00,0x04,'J','F',
            0xFF,0xE1,0x00,0x08,'E','x','i','f',0x00,0x00,
            0xFF,0xED,0x00,0x05,'I','P','T',
            0xFF,0xFE,0x00,0x05,'G','P','S',
            0xFF,0xDA,0x00,0x02,
            0x11,0x22,0x33,0xFF,0xD9
        );
        byte[] out = ImageMetadataStripper.stripForUpload(jpeg, "jpg");
        yes(out.length < jpeg.length, "JPEG metadata segments removed");
        yes(indexOf(out, ascii("Exif")) < 0, "JPEG EXIF removed");
        yes(indexOf(out, ascii("IPT")) < 0, "JPEG IPTC removed");
        yes(indexOf(out, ascii("GPS")) < 0, "JPEG comment removed");
        yes(indexOf(out, new byte[]{(byte)0xFF,(byte)0xDA}) >= 0, "JPEG scan preserved");
    }

    private static void testPng() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        write(b, new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A});
        pngChunk(b, "IHDR", new byte[13]);
        pngChunk(b, "eXIf", ascii("camera-gps"));
        pngChunk(b, "tEXt", ascii("Author\0private"));
        pngChunk(b, "IDAT", new byte[]{1,2,3});
        pngChunk(b, "IEND", new byte[0]);
        byte[] png = b.toByteArray();
        byte[] out = ImageMetadataStripper.stripForUpload(png, ".png");
        // stripForUpload receives extensions without the dot in WebChatServer; verify that contract explicitly.
        byte[] stripped = ImageMetadataStripper.stripForUpload(png, "png");
        yes(Arrays.equals(png, out), "unknown extension spelling remains untouched");
        yes(stripped.length < png.length, "PNG metadata chunks removed");
        yes(indexOf(stripped, ascii("eXIf")) < 0, "PNG eXIf removed");
        yes(indexOf(stripped, ascii("tEXt")) < 0, "PNG text removed");
        yes(indexOf(stripped, ascii("IDAT")) >= 0, "PNG image data preserved");
        yes(indexOf(stripped, ascii("IEND")) >= 0, "PNG terminator preserved");
    }

    private static void testWebp() {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x0C; // EXIF + XMP feature flags
        riffChunk(chunks, "VP8X", vp8x);
        riffChunk(chunks, "EXIF", ascii("gps-camera"));
        riffChunk(chunks, "XMP ", ascii("private-xmp"));
        riffChunk(chunks, "VP8 ", new byte[]{9,8,7,6});
        byte[] body = chunks.toByteArray();
        ByteArrayOutputStream w = new ByteArrayOutputStream();
        write(w, ascii("RIFF")); writeLe32(w, body.length + 4); write(w, ascii("WEBP")); write(w, body);
        byte[] webp = w.toByteArray();
        byte[] out = ImageMetadataStripper.stripForUpload(webp, "webp");
        yes(out.length < webp.length, "WebP metadata chunks removed");
        yes(indexOf(out, ascii("EXIF")) < 0, "WebP EXIF removed");
        yes(indexOf(out, ascii("XMP ")) < 0, "WebP XMP removed");
        int vp8xAt = indexOf(out, ascii("VP8X"));
        yes(vp8xAt >= 0 && (out[vp8xAt + 8] & 0x0C) == 0, "WebP metadata feature flags cleared");
        yes(indexOf(out, ascii("VP8 ")) >= 0, "WebP image payload preserved");
        int riffSize = le32(out, 4);
        yes(riffSize == out.length - 8, "WebP RIFF length rewritten");
    }

    private static void testUntouchedFormats() {
        byte[] gif = ascii("GIF89a-private");
        yes(ImageMetadataStripper.stripForUpload(gif, "gif") == gif, "non-target image format left byte-identical");
        byte[] malformed = ascii("not-a-jpeg-Exif");
        yes(ImageMetadataStripper.stripForUpload(malformed, "jpg") == malformed, "malformed JPEG is not corrupted");
    }

    private static void pngChunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeBe32(out, data.length); write(out, ascii(type)); write(out, data); write(out, new byte[4]);
    }

    private static void riffChunk(ByteArrayOutputStream out, String type, byte[] data) {
        write(out, ascii(type)); writeLe32(out, data.length); write(out, data); if ((data.length & 1) != 0) out.write(0);
    }

    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
    private static byte[] bytes(int... values) { byte[] b = new byte[values.length]; for (int i=0;i<values.length;i++) b[i]=(byte)values[i]; return b; }
    private static void write(ByteArrayOutputStream out, byte[] data) { out.write(data, 0, data.length); }
    private static void writeBe32(ByteArrayOutputStream out, int v) { out.write(v>>>24); out.write(v>>>16); out.write(v>>>8); out.write(v); }
    private static void writeLe32(ByteArrayOutputStream out, int v) { out.write(v); out.write(v>>>8); out.write(v>>>16); out.write(v>>>24); }
    private static int le32(byte[] b, int p) { return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24); }
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer: for (int i=0;i+needle.length<=haystack.length;i++) { for (int j=0;j<needle.length;j++) if (haystack[i+j]!=needle[j]) continue outer; return i; }
        return -1;
    }
    private static void yes(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
}

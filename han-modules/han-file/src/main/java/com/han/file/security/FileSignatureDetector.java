package com.han.file.security;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 文件真实类型探测（魔数）。
 *
 * <p>扩展名和 Content-Type 都由客户端提供、都可以伪造，只有文件头字节是内容本身的属性。
 * 这里只做"够用"的家族级识别：识别出来的家族要能和白名单扩展名对上，
 * 识别为可执行 / 可脚本化内容的一律拒绝。
 */
public final class FileSignatureDetector {

    /**
     * 探测所需的文件头字节数。
     */
    public static final int PROBE_BYTES = 64;

    /**
     * 会被浏览器或操作系统当作可执行内容的家族，任何扩展名下都不允许。
     */
    private static final Set<String> DANGEROUS_FAMILIES = Set.of("elf", "pe", "class", "script", "markup");

    private FileSignatureDetector() {
    }

    /**
     * 探测文件头所属的类型家族。
     *
     * @param header 文件头字节
     * @return 家族标识，无法识别返回 {@code null}
     */
    public static String detect(byte[] header) {
        if (header == null || header.length == 0) {
            return null;
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return "jpeg";
        }
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (startsWithAscii(header, 0, "GIF87a") || startsWithAscii(header, 0, "GIF89a")) {
            return "gif";
        }
        if (startsWithAscii(header, 0, "BM")) {
            return "bmp";
        }
        if (startsWithAscii(header, 0, "RIFF")) {
            if (startsWithAscii(header, 8, "WEBP")) {
                return "webp";
            }
            if (startsWithAscii(header, 8, "WAVE")) {
                return "wav";
            }
            return "riff";
        }
        if (startsWithAscii(header, 0, "%PDF")) {
            return "pdf";
        }
        if (startsWith(header, 0x50, 0x4B, 0x03, 0x04)
                || startsWith(header, 0x50, 0x4B, 0x05, 0x06)
                || startsWith(header, 0x50, 0x4B, 0x07, 0x08)) {
            return "zip";
        }
        if (startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return "ole2";
        }
        if (startsWithAscii(header, 4, "ftyp")) {
            return "isobmff";
        }
        if (startsWith(header, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "matroska";
        }
        if (startsWithAscii(header, 0, "ID3")
                || (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0)) {
            return "mp3";
        }
        if (startsWith(header, 0x7F, 0x45, 0x4C, 0x46)) {
            return "elf";
        }
        if (startsWithAscii(header, 0, "MZ")) {
            return "pe";
        }
        if (startsWith(header, 0xCA, 0xFE, 0xBA, 0xBE)) {
            return "class";
        }
        if (startsWithAscii(header, 0, "#!")) {
            return "script";
        }
        if (startsWith(header, 0x1F, 0x8B)) {
            return "gzip";
        }
        if (startsWithAscii(header, 0, "Rar!")) {
            return "rar";
        }
        if (startsWith(header, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) {
            return "7z";
        }
        if (looksLikeMarkup(header)) {
            return "markup";
        }
        return null;
    }

    /**
     * 判断家族是否属于「任何扩展名下都不允许」的危险内容。
     *
     * @param family 家族标识
     * @return 是否危险
     */
    public static boolean isDangerous(String family) {
        return family != null && DANGEROUS_FAMILIES.contains(family);
    }

    /**
     * HTML / SVG / XML 这类内容一旦被 inline 返回就是同域脚本执行面，统一按 markup 拒绝。
     */
    private static boolean looksLikeMarkup(byte[] header) {
        int index = 0;
        // 跳过 UTF-8 BOM 与前导空白
        if (header.length >= 3 && (header[0] & 0xFF) == 0xEF && (header[1] & 0xFF) == 0xBB && (header[2] & 0xFF) == 0xBF) {
            index = 3;
        }
        while (index < header.length && Character.isWhitespace(header[index])) {
            index++;
        }
        return index < header.length && header[index] == '<';
    }

    private static boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] header, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (header.length < offset + bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (header[offset + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}

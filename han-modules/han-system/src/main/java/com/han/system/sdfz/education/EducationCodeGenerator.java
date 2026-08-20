package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 根据名称生成稳定、可读且在当前数据范围内唯一的业务编码。 */
public final class EducationCodeGenerator {

    private static final int MAX_LENGTH = 64;
    private static final Pattern GRADE_NAME = Pattern.compile("(.{1,32}?年级)");
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private EducationCodeGenerator() {
    }

    public static String unique(String prefix, String name, Predicate<String> exists) {
        String base = code(prefix, name);
        for (int sequence = 1; sequence <= 9999; sequence++) {
            String candidate = withSequence(base, sequence);
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("同名编码过多，请调整名称后重试");
    }

    public static String gradeCode(String className) {
        Matcher matcher = GRADE_NAME.matcher(className == null ? "" : className.trim());
        return code("GRADE", matcher.find() ? matcher.group(1) : className);
    }

    static String code(String prefix, String name) {
        String normalizedPrefix = prefix == null ? "" : prefix.trim().toUpperCase();
        if (!normalizedPrefix.matches("[A-Z][A-Z0-9_]{1,15}")) {
            throw new IllegalArgumentException("编码前缀不合法");
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        String value = name == null ? "" : name.trim();
        for (char character : value.toCharArray()) {
            String[] pinyin = pinyin(character);
            if (pinyin != null && pinyin.length > 0) {
                addAscii(tokens, ascii);
                tokens.add(pinyin[0]);
            } else if (Character.isLetterOrDigit(character)) {
                ascii.append(Character.toUpperCase(character));
            } else {
                addAscii(tokens, ascii);
            }
        }
        addAscii(tokens, ascii);
        if (tokens.isEmpty()) {
            throw new BusinessException("名称无法生成编号");
        }
        return limit(normalizedPrefix + "_" + String.join("_", tokens), MAX_LENGTH);
    }

    private static void addAscii(List<String> tokens, StringBuilder ascii) {
        if (ascii.length() > 0) {
            tokens.add(ascii.toString());
            ascii.setLength(0);
        }
    }

    private static String[] pinyin(char character) {
        try {
            return PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
        } catch (BadHanyuPinyinOutputFormatCombination exception) {
            throw new BusinessException("名称无法生成编号");
        }
    }

    private static String withSequence(String base, int sequence) {
        if (sequence == 1) {
            return base;
        }
        String suffix = "_" + sequence;
        return limit(base, MAX_LENGTH - suffix.length()) + suffix;
    }

    private static String limit(String value, int maxLength) {
        String limited = value.length() <= maxLength ? value : value.substring(0, maxLength);
        return limited.replaceFirst("_+$", "");
    }
}

package com.han.ai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 向量序列化与相似度工具
 * <p>
 * 向量以 JSON 数组文本存于 ai_paragraph.embedding（TEXT 列），
 * 相似度在应用层计算；后续接入 pgvector 时仅需替换存取与检索实现。
 */
public final class AiVectorUtil {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private AiVectorUtil() {
    }

    public static String toJson(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static float[] fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(json, float[].class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 余弦相似度，返回 [-1,1]；维度不一致或零向量返回 0。
     */
    public static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}

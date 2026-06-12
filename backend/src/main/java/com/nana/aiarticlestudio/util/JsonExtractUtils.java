package com.nana.aiarticlestudio.util;

import org.springframework.util.StringUtils;

public class JsonExtractUtils {

    private JsonExtractUtils() {
    }

    public static String extractJsonArray(String text) {
        if (!StringUtils.hasText(text)) {
            throw new RuntimeException("模型返回为空");
        }

        String cleaned = text.trim();

        // 先清理模型思考内容
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length()).trim();
        }

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length()).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");

        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("模型返回中未找到完整 JSON 数组：" + text);
        }

        return cleaned.substring(start, end + 1);
    }
}
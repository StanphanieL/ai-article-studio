package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.OutlineItem;
import com.nana.aiarticlestudio.util.JsonExtractUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import com.nana.aiarticlestudio.model.enums.JsonRepairType;

import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;

@Component
@RequiredArgsConstructor
public class OutlineGeneratorAgent {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    private final JsonRepairAgent jsonRepairAgent;

    private void validateOutline(List<OutlineItem> outline) {
        if (outline == null || outline.size() != 5) {
            throw new RuntimeException("大纲章节数量必须等于 5，当前数量："
                    + (outline == null ? 0 : outline.size()));
        }

        for (OutlineItem item : outline) {
            if (item == null
                    || !org.springframework.util.StringUtils.hasText(item.getHeading())
                    || !org.springframework.util.StringUtils.hasText(item.getDescription())) {
                throw new RuntimeException("大纲字段不完整，必须包含 heading 和 description");
            }
        }
    }

    public String buildPrompt(String topic, String selectedTitle, String style) {
        return """
                你是一个专业的文章大纲策划专家。
                请根据用户选题和已选择标题生成文章大纲。

                用户选题：%s
                已选标题：%s
                文章风格：%s

                输出要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 数组中必须有 5 个章节对象
                6. 每个对象必须包含 heading 和 description 两个字段
                7. 必须保证 JSON 数组完全闭合，最后以 ] 结尾
                8. 不要输出 <think>、</think> 或任何思考过程

                JSON 格式示例：
                [
                  {
                    "heading": "一、章节标题",
                    "description": "章节说明"
                  },
                  {
                    "heading": "二、章节标题",
                    "description": "章节说明"
                  }
                ]
                """.formatted(topic, selectedTitle, style);
    }

    public String callRaw(String prompt) {
        return llmClient.chat(prompt);
    }

    public String callRaw(
            String prompt,
            LlmRequestOptions options
    ) {
        return llmClient.chat(
                prompt,
                options
        );
    }

    public List<OutlineItem> parse(String response) {
        try {
            String json = JsonExtractUtils.extractJsonArray(response);

            return objectMapper.readValue(json, new TypeReference<List<OutlineItem>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("解析大纲失败：" + e.getMessage());
        }
    }

    public List<OutlineItem> parseWithRepair(String response) {
        try {
            List<OutlineItem> result = parse(response);
            validateOutline(result);
            return result;
        } catch (Exception firstException) {
            try {
                String repaired = jsonRepairAgent.repair(
                        JsonRepairType.OUTLINE,
                        response
                );

                List<OutlineItem> repairedResult = parse(repaired);
                validateOutline(repairedResult);
                return repairedResult;
            } catch (Exception secondException) {
                throw new RuntimeException(
                        "解析大纲失败，自动修复后仍失败。首次错误："
                                + firstException.getMessage()
                                + "；修复后错误："
                                + secondException.getMessage()
                );
            }
        }
    }

    public List<OutlineItem> generate(String topic, String selectedTitle, String style) {
        String prompt = buildPrompt(topic, selectedTitle, style);
        String response = callRaw(prompt);
        return parseWithRepair(response);
    }
}
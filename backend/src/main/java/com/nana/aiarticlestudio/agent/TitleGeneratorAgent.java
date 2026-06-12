package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.TitleOption;
import com.nana.aiarticlestudio.util.JsonExtractUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import com.nana.aiarticlestudio.model.enums.JsonRepairType;

import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;

@Component
@RequiredArgsConstructor
public class TitleGeneratorAgent {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    private final JsonRepairAgent jsonRepairAgent;

    private void validateTitleOptions(List<TitleOption> titleOptions) {
        if (titleOptions == null || titleOptions.size() != 3) {
            throw new RuntimeException("标题候选数量必须等于 3，当前数量："
                    + (titleOptions == null ? 0 : titleOptions.size()));
        }

        for (TitleOption option : titleOptions) {
            if (option == null
                    || !org.springframework.util.StringUtils.hasText(option.getTitle())
                    || !org.springframework.util.StringUtils.hasText(option.getReason())) {
                throw new RuntimeException("标题候选字段不完整，必须包含 title 和 reason");
            }
        }
    }

    public String buildPrompt(String topic, String style) {
        return """
                你是一个专业的爆款文章标题策划专家。
                请根据用户选题生成 3 个标题候选。

                用户选题：%s
                文章风格：%s

                输出要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 数组中必须有 3 个对象
                6. 每个对象必须包含 title 和 reason 两个字段
                7. 必须保证 JSON 数组完全闭合，最后以 ] 结尾
                8. 不要输出 <think>、</think> 或任何思考过程

                JSON 格式示例：
                [
                  {
                    "title": "标题1",
                    "reason": "推荐理由1"
                  },
                  {
                    "title": "标题2",
                    "reason": "推荐理由2"
                  },
                  {
                    "title": "标题3",
                    "reason": "推荐理由3"
                  }
                ]
                """.formatted(topic, style);
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

    public List<TitleOption> parse(String response) {
        try {
            String json = JsonExtractUtils.extractJsonArray(response);

            return objectMapper.readValue(json, new TypeReference<List<TitleOption>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("解析标题失败：" + e.getMessage());
        }
    }

    public List<TitleOption> parseWithRepair(String response) {
        try {
            List<TitleOption> result = parse(response);
            validateTitleOptions(result);
            return result;
        } catch (Exception firstException) {
            try {
                String repaired = jsonRepairAgent.repair(
                        JsonRepairType.TITLE_OPTIONS,
                        response
                );

                List<TitleOption> repairedResult = parse(repaired);
                validateTitleOptions(repairedResult);
                return repairedResult;
            } catch (Exception secondException) {
                throw new RuntimeException(
                        "解析标题失败，自动修复后仍失败。首次错误："
                                + firstException.getMessage()
                                + "；修复后错误："
                                + secondException.getMessage()
                );
            }
        }
    }

    public List<TitleOption> generate(String topic, String style) {
        String prompt = buildPrompt(topic, style);
        String response = callRaw(prompt);
        return parseWithRepair(response);
    }
}
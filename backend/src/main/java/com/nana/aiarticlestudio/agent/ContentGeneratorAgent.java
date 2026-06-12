package com.nana.aiarticlestudio.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;

@Component
@RequiredArgsConstructor
public class ContentGeneratorAgent {

    private final LlmClient llmClient;

    public String buildPrompt(String topic, String selectedTitle, String outline, String style) {
        return """
                你是一个专业的长文写作专家。
                请根据用户选题、已选标题和文章大纲，生成一篇结构清晰的 Markdown 正文。

                用户选题：%s
                已选标题：%s
                文章风格：%s
                文章大纲：%s

                输出要求：
                1. 只输出 Markdown 正文
                2. 不要输出任何解释性文字
                3. 不要输出“以下是正文”等前置说明
                4. 不要输出 <think>、</think> 或任何思考过程
                5. 必须包含一级标题
                6. 必须包含多个二级标题
                7. 每个章节至少包含 2 段正文
                8. 语言清晰，适合普通读者阅读

                请直接输出 Markdown 正文。
                """.formatted(topic, selectedTitle, style, outline);
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

    public String clean(String response) {
        if (response == null) {
            return "";
        }

        return response.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    public String generate(String topic, String selectedTitle, String outline, String style) {
        String prompt = buildPrompt(topic, selectedTitle, outline, style);
        String response = callRaw(prompt);
        return clean(response);
    }
}
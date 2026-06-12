package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ImagePromptAgent {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    public String buildPrompt(String topic,
                              String selectedTitle,
                              String outline,
                              String content,
                              String style) {
        return """
                你是一名专业的 AI 图像提示词策划专家，擅长根据文章内容设计配图方案。

                请根据下面文章信息，生成 3 个图片提示词方案。

                用户选题：%s
                已选标题：%s
                文章风格：%s
                文章大纲：%s
                文章正文：%s

                输出要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. 数组中必须有 3 个对象
                7. 每个对象必须包含 imageTitle、usageScene、promptZh、promptEn
                8. promptZh 用中文，适合用户理解
                9. promptEn 用英文，适合直接给图片生成模型使用
                10. 图片风格要和文章风格匹配
                11. 不要生成低俗、暴力、违法内容

                字段说明：
                - imageTitle：图片方案标题
                - usageScene：图片使用场景，例如“文章封面”“正文配图”“小红书封面”
                - promptZh：中文图片提示词
                - promptEn：英文图片提示词

                示例格式：
                [
                  {
                    "imageTitle": "科技感效率封面",
                    "usageScene": "文章封面",
                    "promptZh": "一张现代科技感封面图，表现 AI 工具提升工作效率，画面干净，适合文章封面",
                    "promptEn": "A modern technology-style cover image showing AI tools improving productivity, clean composition, suitable for an article cover"
                  }
                ]

                请直接输出 JSON 数组。
                """.formatted(topic, selectedTitle, style, outline, content);
    }

    public String callRaw(String prompt) {
        return llmClient.chat(prompt);
    }

    public String clean(String response) {
        if (response == null) {
            return "";
        }

        return response
                .replaceAll("(?s)<think>.*?</think>", "")
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    public List<ImagePromptOption> parse(String response) throws Exception {
        String cleaned = clean(response);

        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");

        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("模型返回中未找到 JSON 数组：" + response);
        }

        String jsonArray = cleaned.substring(start, end + 1);

        return objectMapper.readValue(
                jsonArray,
                new TypeReference<List<ImagePromptOption>>() {}
        );
    }

    public List<ImagePromptOption> generate(String topic,
                                            String selectedTitle,
                                            String outline,
                                            String content,
                                            String style) throws Exception {
        String prompt = buildPrompt(topic, selectedTitle, outline, content, style);
        String response = callRaw(prompt);
        return parse(response);
    }
}
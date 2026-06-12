package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;

@Component
@RequiredArgsConstructor
public class ImagePromptAgent {

    /**
     * 最少生成 3 张图：
     * 1 张封面 + 至少 2 张正文图。
     */
    private static final int MIN_IMAGE_COUNT = 3;

    /**
     * 最多生成 8 张，避免生图时间和费用失控。
     */
    private static final int MAX_IMAGE_COUNT = 8;

    /**
     * 正文没有标准 Markdown 标题时，
     * 每约 900 个非空白字符增加一张正文图。
     */
    private static final int CHARS_PER_IMAGE = 900;

    /**
     * 匹配 Markdown 二级标题：
     * ## 标题
     */
    private static final Pattern SECOND_LEVEL_HEADING_PATTERN =
            Pattern.compile("(?m)^##\\s+");

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper;

    /**
     * 根据正文结构自动计算图片数量。
     */
    public int calculateImageCount(String content) {
        if (!StringUtils.hasText(content)) {
            return MIN_IMAGE_COUNT;
        }

        int headingCount = countSecondLevelHeadings(content);

        String compactContent =
                content.replaceAll("\\s+", "");

        int contentLength =
                compactContent.length();

        int countByLength =
                (int) Math.ceil(
                        contentLength
                                / (double) CHARS_PER_IMAGE
                );

        /*
         * headingCount + 1：
         * 1 张封面 + 每个二级章节约 1 张图。
         *
         * countByLength + 1：
         * 1 张封面 + 根据正文长度计算正文图。
         */
        int expectedCount = Math.max(
                headingCount + 1,
                countByLength + 1
        );

        return clampImageCount(expectedCount);
    }

    /**
     * 构造图片提示词生成 Prompt。
     */
    public String buildPrompt(
            String topic,
            String selectedTitle,
            String outline,
            String content,
            String style,
            int imageCount
    ) {
        int safeImageCount =
                clampImageCount(imageCount);

        return """
                你是一名专业的 AI 图像提示词策划专家，
                擅长根据文章内容设计结构清晰、风格统一的文章配图方案。

                请根据下面的文章信息，严格生成 %d 个图片提示词方案。

                用户选题：%s

                已选标题：%s

                文章风格：%s

                文章大纲：%s

                文章正文：%s

                配图规划要求：

                1. 第 1 个对象必须是“文章封面”
                2. 其余图片按照文章正文的章节顺序排列
                3. 每个主要章节最多对应一张核心配图
                4. 不同图片的构图和内容必须有明显区别
                5. 不要生成多个含义重复的图片方案
                6. usageScene 必须清楚说明图片对应的位置
                7. 正文配图的 usageScene 建议写成：
                   “第一节正文配图：章节名称”
                8. 所有图片应保持统一的视觉风格
                9. 提示词中尽量避免生成大段文字和复杂字符
                10. 图片应适合用于正式文章，而不是仅用于社交媒体封面

                输出要求：

                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. 数组中必须严格包含 %d 个对象
                7. 每个对象必须包含：
                   imageTitle、usageScene、promptZh、promptEn
                8. promptZh 使用中文，便于用户理解
                9. promptEn 使用英文，适合直接传给图片生成模型
                10. 图片风格必须与文章风格匹配
                11. 不要生成低俗、暴力、违法内容

                字段说明：

                - imageTitle：图片方案标题
                - usageScene：图片在文章中的使用位置
                - promptZh：中文图片生成提示词
                - promptEn：英文图片生成提示词

                示例对象：

                {
                  "imageTitle": "科技感效率封面",
                  "usageScene": "文章封面",
                  "promptZh": "现代科技感文章封面，表现 AI 工具提升工作效率，画面干净，主体明确，不包含大段文字",
                  "promptEn": "A modern technology-style article cover showing AI tools improving productivity, clean composition, clear subject, without large blocks of text"
                }

                请直接输出完整 JSON 数组。
                """.formatted(
                safeImageCount,
                topic,
                selectedTitle,
                style,
                outline,
                content,
                safeImageCount
        );
    }

    /**
     * 保留原来的方法签名，避免其他代码调用时报错。
     */
    public String buildPrompt(
            String topic,
            String selectedTitle,
            String outline,
            String content,
            String style
    ) {
        int imageCount =
                calculateImageCount(content);

        return buildPrompt(
                topic,
                selectedTitle,
                outline,
                content,
                style,
                imageCount
        );
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

        return response
                .replaceAll(
                        "(?s)<think>.*?</think>",
                        ""
                )
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    /**
     * 解析并检查图片提示词数量。
     */
    public List<ImagePromptOption> parse(
            String response,
            int expectedCount
    ) throws Exception {

        String cleaned =
                clean(response);

        int start =
                cleaned.indexOf("[");

        int end =
                cleaned.lastIndexOf("]");

        if (start < 0
                || end < 0
                || end <= start) {

            throw new RuntimeException(
                    "模型返回中未找到 JSON 数组："
                            + response
            );
        }

        String jsonArray =
                cleaned.substring(
                        start,
                        end + 1
                );

        List<ImagePromptOption> result =
                objectMapper.readValue(
                        jsonArray,
                        new TypeReference<
                                List<ImagePromptOption>
                                >() {
                        }
                );

        validateImagePrompts(
                result,
                expectedCount
        );

        return result;
    }

    /**
     * 保留原来的 parse 方法。
     */
    public List<ImagePromptOption> parse(
            String response
    ) throws Exception {

        String cleaned =
                clean(response);

        int start =
                cleaned.indexOf("[");

        int end =
                cleaned.lastIndexOf("]");

        if (start < 0
                || end < 0
                || end <= start) {

            throw new RuntimeException(
                    "模型返回中未找到 JSON 数组："
                            + response
            );
        }

        String jsonArray =
                cleaned.substring(
                        start,
                        end + 1
                );

        return objectMapper.readValue(
                jsonArray,
                new TypeReference<
                        List<ImagePromptOption>
                        >() {
                }
        );
    }

    /**
     * 兼容原来的 generate 方法。
     */
    public List<ImagePromptOption> generate(
            String topic,
            String selectedTitle,
            String outline,
            String content,
            String style
    ) throws Exception {

        int imageCount =
                calculateImageCount(content);

        String prompt =
                buildPrompt(
                        topic,
                        selectedTitle,
                        outline,
                        content,
                        style,
                        imageCount
                );

        String response =
                callRaw(prompt);

        return parse(
                response,
                imageCount
        );
    }

    private int countSecondLevelHeadings(
            String content
    ) {
        Matcher matcher =
                SECOND_LEVEL_HEADING_PATTERN
                        .matcher(content);

        int count = 0;

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private int clampImageCount(
            int imageCount
    ) {
        return Math.max(
                MIN_IMAGE_COUNT,
                Math.min(
                        MAX_IMAGE_COUNT,
                        imageCount
                )
        );
    }

    /**
     * 检查数量和字段完整性。
     */
    private void validateImagePrompts(
            List<ImagePromptOption> prompts,
            int expectedCount
    ) {
        if (prompts == null
                || prompts.isEmpty()) {

            throw new RuntimeException(
                    "模型没有返回任何图片提示词"
            );
        }

        if (prompts.size() != expectedCount) {
            throw new RuntimeException(
                    "期望生成 "
                            + expectedCount
                            + " 个图片提示词，"
                            + "模型实际返回 "
                            + prompts.size()
                            + " 个，请重新生成"
            );
        }

        for (int i = 0;
             i < prompts.size();
             i++) {

            ImagePromptOption item =
                    prompts.get(i);

            if (item == null) {
                throw new RuntimeException(
                        "第 "
                                + (i + 1)
                                + " 个图片提示词为空"
                );
            }

            if (!StringUtils.hasText(
                    item.getImageTitle()
            )) {
                throw new RuntimeException(
                        "第 "
                                + (i + 1)
                                + " 个图片方案缺少 imageTitle"
                );
            }

            if (!StringUtils.hasText(
                    item.getUsageScene()
            )) {
                throw new RuntimeException(
                        "第 "
                                + (i + 1)
                                + " 个图片方案缺少 usageScene"
                );
            }

            if (!StringUtils.hasText(
                    item.getPromptZh()
            )) {
                throw new RuntimeException(
                        "第 "
                                + (i + 1)
                                + " 个图片方案缺少 promptZh"
                );
            }

            if (!StringUtils.hasText(
                    item.getPromptEn()
            )) {
                throw new RuntimeException(
                        "第 "
                                + (i + 1)
                                + " 个图片方案缺少 promptEn"
                );
            }
        }
    }
}
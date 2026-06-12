package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ArticleComposeAgent {

    private final ObjectMapper objectMapper;

    /**
     * 将标题、正文和图片结果组合成完整 Markdown。
     */
    public String compose(
            String title,
            String content,
            String imageResultsJson
    ) throws Exception {

        if (!StringUtils.hasText(content)) {
            throw new RuntimeException(
                    "文章正文为空，无法进行图文合成"
            );
        }

        List<ImageResultOption> images =
                parseImageResults(imageResultsJson);

        List<String> sections =
                splitBySecondLevelHeading(content);

        StringBuilder markdown =
                new StringBuilder();

        // 正文中没有一级标题时，自动添加文章标题。
        appendTitleIfNecessary(
                markdown,
                title,
                content
        );

        int imageIndex = 0;

        /*
         * 第一张图作为文章首图，
         * 放在一级标题后、正文前。
         */
        if (!images.isEmpty()) {
            appendImage(
                    markdown,
                    images.get(0)
            );

            imageIndex = 1;
        }

        /*
         * 依次写入正文各部分。
         * 每写完一部分，就尝试插入下一张图。
         */
        for (String section : sections) {
            if (!StringUtils.hasText(section)) {
                continue;
            }

            markdown
                    .append(section.trim())
                    .append("\n\n");

            if (imageIndex < images.size()) {
                appendImage(
                        markdown,
                        images.get(imageIndex)
                );

                imageIndex++;
            }
        }

        /*
         * 如果图片数量多于正文部分，
         * 将剩余图片追加到文章末尾。
         */
        while (imageIndex < images.size()) {
            appendImage(
                    markdown,
                    images.get(imageIndex)
            );

            imageIndex++;
        }

        return markdown.toString().trim();
    }

    /**
     * 解析数据库中的 image_results JSON。
     */
    private List<ImageResultOption> parseImageResults(
            String imageResultsJson
    ) throws Exception {

        if (!StringUtils.hasText(imageResultsJson)) {
            return new ArrayList<>();
        }

        return objectMapper.readValue(
                imageResultsJson,
                new TypeReference<
                        List<ImageResultOption>
                        >() {
                }
        );
    }

    /**
     * 根据 Markdown 二级标题拆分正文。
     *
     * 示例：
     *
     * 开头内容
     * ## 第一部分
     * 第一部分正文
     * ## 第二部分
     * 第二部分正文
     */
    private List<String> splitBySecondLevelHeading(
            String content
    ) {
        String normalized = content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        String[] parts = normalized.split(
                "(?m)(?=^##\\s+)"
        );

        List<String> sections =
                new ArrayList<>();

        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                sections.add(part.trim());
            }
        }

        if (sections.isEmpty()) {
            sections.add(normalized);
        }

        return sections;
    }

    /**
     * 正文中没有一级标题时，自动添加标题。
     */
    private void appendTitleIfNecessary(
            StringBuilder markdown,
            String title,
            String content
    ) {
        if (!StringUtils.hasText(title)) {
            return;
        }

        String trimmedContent =
                content.trim();

        if (trimmedContent.startsWith("# ")) {
            return;
        }

        markdown
                .append("# ")
                .append(title.trim())
                .append("\n\n");
    }

    /**
     * 将图片及其说明写入 Markdown。
     */
    private void appendImage(
            StringBuilder markdown,
            ImageResultOption image
    ) {
        if (image == null
                || !StringUtils.hasText(
                image.getImageUrl()
        )) {
            return;
        }

        String imageTitle =
                StringUtils.hasText(
                        image.getImageTitle()
                )
                        ? image.getImageTitle().trim()
                        : "文章配图";

        markdown
                .append("![")
                .append(
                        escapeMarkdownText(
                                imageTitle
                        )
                )
                .append("](")
                .append(
                        image.getImageUrl().trim()
                )
                .append(")")
                .append("\n\n");

        List<String> captionParts =
                new ArrayList<>();

        if (StringUtils.hasText(
                image.getUsageScene()
        )) {
            captionParts.add(
                    image.getUsageScene().trim()
            );
        }

        if (StringUtils.hasText(
                image.getAuthor()
        )) {
            captionParts.add(
                    "来源："
                            + image.getAuthor().trim()
            );
        } else if (StringUtils.hasText(
                image.getSource()
        )) {
            captionParts.add(
                    "来源："
                            + image.getSource().trim()
            );
        }

        if (!captionParts.isEmpty()) {
            markdown
                    .append("> ")
                    .append(
                            String.join(
                                    " ｜ ",
                                    captionParts
                            )
                    )
                    .append("\n\n");
        }
    }

    /**
     * 防止图片标题破坏 Markdown 的方括号结构。
     */
    private String escapeMarkdownText(
            String value
    ) {
        return value
                .replace("[", "\\[")
                .replace("]", "\\]");
    }
}
package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ArticleComposeAgent {

    /**
     * 匹配 Markdown 二级标题，例如：
     * ## 第一章
     */
    private static final Pattern SECOND_LEVEL_HEADING_PATTERN =
            Pattern.compile("(?m)^##\\s+.*$");

    private final ObjectMapper objectMapper;

    /**
     * 将标题、正文和图片结果组合成最终 Markdown。
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

        /*
         * 使用新的 ArrayList 包装，
         * 后面需要从列表中移除封面图。
         */
        List<ImageResultOption> images =
                new ArrayList<>(
                        parseImageResults(
                                imageResultsJson
                        )
                );

        String normalizedContent =
                normalizeContent(content);

        /*
         * 优先使用数据库中已经确认的标题。
         * 如果标题为空，则尝试读取正文中的一级标题。
         */
        String documentTitle =
                resolveDocumentTitle(
                        title,
                        normalizedContent
                );

        /*
         * 正文中原本包含一级标题。
         * 这里先将它移除，稍后统一重新写入，
         * 避免封面图跑到标题上方。
         */
        String articleBody =
                removeLeadingFirstLevelHeading(
                        normalizedContent
                );

        /*
         * 找出封面图，并从普通章节图片列表中移除。
         */
        ImageResultOption coverImage =
                takeCoverImage(images);

        /*
         * 将正文拆分为：
         * 1. 开头导语
         * 2. 多个二级章节
         */
        ArticleStructure structure =
                parseArticleStructure(
                        articleBody
                );

        StringBuilder markdown =
                new StringBuilder();

        /*
         * 第一步：写入文章一级标题。
         */
        if (StringUtils.hasText(documentTitle)) {
            markdown
                    .append("# ")
                    .append(documentTitle.trim())
                    .append("\n\n");
        }

        /*
         * 第二步：在一级标题下方插入封面图。
         */
        if (coverImage != null) {
            appendImage(
                    markdown,
                    coverImage
            );
        }

        /*
         * 第三步：写入开头导语。
         *
         * 注意：
         * 导语不是正式章节，不消耗章节图片。
         * 这是修复图片整体错位的关键。
         */
        if (StringUtils.hasText(
                structure.introduction()
        )) {
            markdown
                    .append(
                            structure
                                    .introduction()
                                    .trim()
                    )
                    .append("\n\n");
        }

        int imageIndex = 0;

        /*
         * 第四步：依次写入正式章节。
         *
         * 顺序固定为：
         * 二级标题
         * → 对应章节图片
         * → 章节正文
         */
        for (ArticleSection section
                : structure.sections()) {

            markdown
                    .append(
                            section.heading()
                    )
                    .append("\n\n");

            if (imageIndex < images.size()) {
                appendImage(
                        markdown,
                        images.get(imageIndex)
                );

                imageIndex++;
            }

            if (StringUtils.hasText(
                    section.body()
            )) {
                markdown
                        .append(
                                section
                                        .body()
                                        .trim()
                        )
                        .append("\n\n");
            }
        }

        /*
         * 如果图片数量比正式章节更多，
         * 将剩余图片追加到文章末尾。
         */
        while (imageIndex < images.size()) {
            appendImage(
                    markdown,
                    images.get(imageIndex)
            );

            imageIndex++;
        }

        return markdown
                .toString()
                .trim();
    }

    /**
     * 统一正文换行格式。
     */
    private String normalizeContent(
            String content
    ) {
        return content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    /**
     * 确定最终使用的文章标题。
     */
    private String resolveDocumentTitle(
            String title,
            String content
    ) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }

        if (content.startsWith("# ")) {
            int lineEnd =
                    content.indexOf('\n');

            if (lineEnd < 0) {
                return content
                        .substring(2)
                        .trim();
            }

            return content
                    .substring(2, lineEnd)
                    .trim();
        }

        return "";
    }

    /**
     * 删除正文开头原有的一级标题。
     *
     * 例如：
     *
     * # 文章标题
     *
     * 开头导语
     *
     * 会变为：
     *
     * 开头导语
     */
    private String removeLeadingFirstLevelHeading(
            String content
    ) {
        if (!content.startsWith("# ")) {
            return content;
        }

        int lineEnd =
                content.indexOf('\n');

        if (lineEnd < 0) {
            return "";
        }

        return content
                .substring(lineEnd + 1)
                .trim();
    }

    /**
     * 查找封面图。
     *
     * 优先查找 usageScene 中包含：
     * 封面 / cover
     *
     * 如果没有明确标记，则默认使用第一张图。
     */
    private ImageResultOption takeCoverImage(
            List<ImageResultOption> images
    ) {
        if (images.isEmpty()) {
            return null;
        }

        for (int i = 0;
             i < images.size();
             i++) {

            ImageResultOption image =
                    images.get(i);

            if (image == null) {
                continue;
            }

            String usageScene =
                    image.getUsageScene();

            if (!StringUtils.hasText(
                    usageScene
            )) {
                continue;
            }

            String normalizedUsageScene =
                    usageScene
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (normalizedUsageScene
                    .contains("封面")
                    || normalizedUsageScene
                    .contains("cover")) {

                return images.remove(i);
            }
        }

        /*
         * 未找到明确封面时，
         * 使用第一张图作为封面。
         */
        return images.remove(0);
    }

    /**
     * 将正文拆成：
     *
     * introduction：第一个 ## 之前的导语
     * sections：所有正式二级章节
     */
    private ArticleStructure parseArticleStructure(
            String articleBody
    ) {
        Matcher matcher =
                SECOND_LEVEL_HEADING_PATTERN
                        .matcher(articleBody);

        String introduction = "";

        List<ArticleSection> sections =
                new ArrayList<>();

        String currentHeading = null;

        int currentBodyStart = 0;

        boolean foundHeading = false;

        while (matcher.find()) {
            if (!foundHeading) {
                /*
                 * 第一个二级标题之前的内容，
                 * 只作为文章导语。
                 */
                introduction =
                        articleBody
                                .substring(
                                        0,
                                        matcher.start()
                                )
                                .trim();

                foundHeading = true;
            } else {
                /*
                 * 保存上一个章节的正文。
                 */
                String previousBody =
                        articleBody
                                .substring(
                                        currentBodyStart,
                                        matcher.start()
                                )
                                .trim();

                sections.add(
                        new ArticleSection(
                                currentHeading,
                                previousBody
                        )
                );
            }

            currentHeading =
                    matcher.group().trim();

            currentBodyStart =
                    matcher.end();
        }

        if (foundHeading) {
            /*
             * 保存最后一个章节。
             */
            String lastBody =
                    articleBody
                            .substring(
                                    currentBodyStart
                            )
                            .trim();

            sections.add(
                    new ArticleSection(
                            currentHeading,
                            lastBody
                    )
            );
        } else {
            /*
             * 没有二级标题时，
             * 整篇正文都作为导语处理。
             */
            introduction =
                    articleBody.trim();
        }

        return new ArticleStructure(
                introduction,
                sections
        );
    }

    /**
     * 解析数据库中的 image_results JSON。
     */
    private List<ImageResultOption> parseImageResults(
            String imageResultsJson
    ) throws Exception {

        if (!StringUtils.hasText(
                imageResultsJson
        )) {
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
     * 写入图片及其说明。
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
                        ? image
                        .getImageTitle()
                        .trim()
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
                        image
                                .getImageUrl()
                                .trim()
                )
                .append(")")
                .append("\n\n");

        List<String> captionParts =
                new ArrayList<>();

        if (StringUtils.hasText(
                image.getUsageScene()
        )) {
            captionParts.add(
                    image
                            .getUsageScene()
                            .trim()
            );
        }

        if (StringUtils.hasText(
                image.getAuthor()
        )) {
            captionParts.add(
                    "来源："
                            + image
                            .getAuthor()
                            .trim()
            );
        } else if (StringUtils.hasText(
                image.getSource()
        )) {
            captionParts.add(
                    "来源："
                            + image
                            .getSource()
                            .trim()
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
     * 防止图片标题破坏 Markdown 格式。
     */
    private String escapeMarkdownText(
            String value
    ) {
        return value
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    /**
     * 拆分后的文章结构。
     */
    private record ArticleStructure(
            String introduction,
            List<ArticleSection> sections
    ) {
    }

    /**
     * 单个正式章节。
     */
    private record ArticleSection(
            String heading,
            String body
    ) {
    }
}
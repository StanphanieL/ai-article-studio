package com.nana.aiarticlestudio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.mapper.ArticleMapper;
import com.nana.aiarticlestudio.model.entity.Article;
import com.nana.aiarticlestudio.model.vo.ArticleExportFile;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import lombok.RequiredArgsConstructor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ArticleExportService {

    private final ArticleMapper articleMapper;

    private final ObjectMapper objectMapper;

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(20)
                    )
                    .build();

    private final Parser markdownParser =
            Parser.builder().build();

    private final HtmlRenderer htmlRenderer =
            HtmlRenderer.builder().build();

    public ArticleExportFile exportHtml(
            String taskId
    ) {
        try {
            Article article =
                    getArticle(taskId);

            List<ExportImage> images =
                    prepareImages(article);

            Map<String, String> replacements =
                    new LinkedHashMap<>();

            for (ExportImage image : images) {
                String base64 =
                        Base64.getEncoder()
                                .encodeToString(
                                        image.binary()
                                                .content()
                                );

                String dataUrl =
                        "data:"
                                + image.binary()
                                .contentType()
                                + ";base64,"
                                + base64;

                replacements.put(
                        image.originalUrl(),
                        dataUrl
                );
            }

            String markdown =
                    replaceImageUrls(
                            article.getFinalMarkdown(),
                            replacements
                    );

            String title =
                    resolveTitle(article);

            String html =
                    buildHtml(
                            title,
                            markdown
                    );

            return new ArticleExportFile(
                    sanitizeFileName(title)
                            + ".html",
                    "text/html;charset=UTF-8",
                    html.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "导出 HTML 失败："
                            + e.getMessage(),
                    e
            );
        }
    }

    public ArticleExportFile exportZip(
            String taskId
    ) {
        try {
            Article article =
                    getArticle(taskId);

            List<ExportImage> images =
                    prepareImages(article);

            Map<String, String> replacements =
                    new LinkedHashMap<>();

            for (ExportImage image : images) {
                replacements.put(
                        image.originalUrl(),
                        image.relativePath()
                );
            }

            String markdown =
                    replaceImageUrls(
                            article.getFinalMarkdown(),
                            replacements
                    );

            String title =
                    resolveTitle(article);

            String html =
                    buildHtml(
                            title,
                            markdown
                    );

            byte[] metadata =
                    buildMetadata(
                            article,
                            images.size()
                    );

            byte[] zipContent =
                    buildZip(
                            markdown,
                            html,
                            metadata,
                            images
                    );

            return new ArticleExportFile(
                    sanitizeFileName(title)
                            + ".zip",
                    "application/zip",
                    zipContent
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "导出 ZIP 失败："
                            + e.getMessage(),
                    e
            );
        }
    }

    private Article getArticle(
            String taskId
    ) {
        Article article =
                articleMapper.selectByTaskId(
                        taskId
                );

        if (article == null) {
            throw new RuntimeException(
                    "文章任务不存在"
            );
        }

        if (!StringUtils.hasText(
                article.getFinalMarkdown()
        )) {
            throw new RuntimeException(
                    "请先生成最终图文稿"
            );
        }

        return article;
    }

    private List<ImageResultOption>
    parseImageResults(
            Article article
    ) throws Exception {

        if (!StringUtils.hasText(
                article.getImageResults()
        )) {
            return new ArrayList<>();
        }

        return objectMapper.readValue(
                article.getImageResults(),
                new TypeReference<
                        List<ImageResultOption>
                        >() {
                }
        );
    }

    private List<ExportImage> prepareImages(
            Article article
    ) throws Exception {

        List<ImageResultOption> imageResults =
                parseImageResults(article);

        List<ExportImage> exportImages =
                new ArrayList<>();

        Set<String> processedUrls =
                new HashSet<>();

        int index = 1;

        for (ImageResultOption item
                : imageResults) {

            if (item == null
                    || !StringUtils.hasText(
                    item.getImageUrl()
            )) {
                continue;
            }

            String imageUrl =
                    item.getImageUrl().trim();

            if (!processedUrls.add(imageUrl)) {
                continue;
            }

            ImageBinary binary =
                    loadImage(imageUrl);

            String relativePath =
                    "images/image-%02d%s"
                            .formatted(
                                    index,
                                    binary.extension()
                            );

            exportImages.add(
                    new ExportImage(
                            imageUrl,
                            relativePath,
                            binary
                    )
            );

            index++;
        }

        return exportImages;
    }

    private ImageBinary loadImage(
            String imageUrl
    ) throws Exception {

        URI uri =
                URI.create(imageUrl);

        String host =
                uri.getHost();

        String path =
                uri.getPath();

        boolean localHost =
                host == null
                        || "localhost"
                        .equalsIgnoreCase(host)
                        || "127.0.0.1"
                        .equals(host)
                        || "::1"
                        .equals(host);

        if (localHost
                && path != null
                && path.startsWith(
                "/uploads/images/"
        )) {
            return loadLocalImage(path);
        }

        return downloadRemoteImage(
                imageUrl
        );
    }

    private ImageBinary loadLocalImage(
            String imagePath
    ) throws Exception {

        String fileName =
                Path.of(imagePath)
                        .getFileName()
                        .toString();

        Path baseDir =
                Paths.get(imageLocalDir)
                        .toAbsolutePath()
                        .normalize();

        Path file =
                baseDir.resolve(fileName)
                        .normalize();

        if (!file.startsWith(baseDir)) {
            throw new RuntimeException(
                    "非法图片路径："
                            + imagePath
            );
        }

        if (!Files.exists(file)) {
            throw new RuntimeException(
                    "本地图片不存在："
                            + fileName
            );
        }

        byte[] content =
                Files.readAllBytes(file);

        String contentType =
                Files.probeContentType(file);

        if (!StringUtils.hasText(
                contentType
        )) {
            contentType =
                    contentTypeFromFileName(
                            fileName
                    );
        }

        String extension =
                resolveExtension(
                        fileName,
                        contentType
                );

        return new ImageBinary(
                content,
                contentType,
                extension
        );
    }

    private ImageBinary downloadRemoteImage(
            String imageUrl
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(imageUrl)
                        )
                        .timeout(
                                Duration.ofSeconds(60)
                        )
                        .header(
                                "User-Agent",
                                "AI-Article-Studio/1.0"
                        )
                        .GET()
                        .build();

        HttpResponse<byte[]> response =
                httpClient.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofByteArray()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "远程图片下载失败，状态码："
                            + response.statusCode()
                            + "，地址："
                            + imageUrl
            );
        }

        String contentType =
                response.headers()
                        .firstValue(
                                "Content-Type"
                        )
                        .map(
                                value ->
                                        value.split(";")[0]
                                                .trim()
                        )
                        .orElse(
                                contentTypeFromFileName(
                                        imageUrl
                                )
                        );

        if (!contentType
                .toLowerCase(Locale.ROOT)
                .startsWith("image/")) {
            throw new RuntimeException(
                    "远程地址返回的不是图片："
                            + imageUrl
            );
        }

        String extension =
                resolveExtension(
                        imageUrl,
                        contentType
                );

        return new ImageBinary(
                response.body(),
                contentType,
                extension
        );
    }

    private String replaceImageUrls(
            String markdown,
            Map<String, String> replacements
    ) {
        String result = markdown;

        for (Map.Entry<String, String> entry
                : replacements.entrySet()) {
            result =
                    result.replace(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }

    private String buildHtml(
            String title,
            String markdown
    ) {
        Node document =
                markdownParser.parse(
                        markdown
                );

        String body =
                htmlRenderer.render(
                        document
                );

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>__TITLE__</title>
                  <style>
                    * {
                      box-sizing: border-box;
                    }

                    body {
                      margin: 0;
                      padding: 40px 20px;
                      color: #1f2328;
                      background: #f6f8fa;
                      font-family: -apple-system, BlinkMacSystemFont,
                        "Segoe UI", "PingFang SC", "Microsoft YaHei",
                        sans-serif;
                      line-height: 1.8;
                    }

                    article {
                      width: 100%;
                      max-width: 900px;
                      margin: 0 auto;
                      padding: 48px 64px;
                      background: #ffffff;
                      border-radius: 12px;
                      box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
                    }

                    h1 {
                      margin-top: 0;
                      font-size: 36px;
                      line-height: 1.35;
                    }

                    h2 {
                      margin-top: 40px;
                      padding-bottom: 10px;
                      border-bottom: 1px solid #eaecef;
                      font-size: 26px;
                    }

                    h3 {
                      margin-top: 28px;
                      font-size: 21px;
                    }

                    p {
                      margin: 16px 0;
                    }

                    img {
                      display: block;
                      max-width: 100%;
                      height: auto;
                      margin: 24px auto 12px;
                      border-radius: 10px;
                    }

                    blockquote {
                      margin: 12px 0 28px;
                      padding: 10px 16px;
                      color: #57606a;
                      background: #f6f8fa;
                      border-left: 4px solid #d0d7de;
                    }

                    pre {
                      overflow-x: auto;
                      padding: 16px;
                      background: #f6f8fa;
                      border-radius: 8px;
                    }

                    code {
                      font-family: Consolas, Monaco, monospace;
                    }

                    a {
                      color: #1677ff;
                    }

                    @media (max-width: 720px) {
                      body {
                        padding: 0;
                      }

                      article {
                        padding: 28px 20px;
                        border-radius: 0;
                      }

                      h1 {
                        font-size: 29px;
                      }

                      h2 {
                        font-size: 23px;
                      }
                    }
                  </style>
                </head>
                <body>
                  <article>
                __BODY__
                  </article>
                </body>
                </html>
                """
                .replace(
                        "__TITLE__",
                        escapeHtml(title)
                )
                .replace(
                        "__BODY__",
                        body
                );
    }

    private byte[] buildMetadata(
            Article article,
            int imageCount
    ) throws Exception {

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "taskId",
                article.getTaskId()
        );

        metadata.put(
                "topic",
                article.getTopic()
        );

        metadata.put(
                "title",
                resolveTitle(article)
        );

        metadata.put(
                "style",
                article.getStyle()
        );

        metadata.put(
                "phase",
                article.getPhase()
        );

        metadata.put(
                "status",
                article.getStatus()
        );

        metadata.put(
                "imageCount",
                imageCount
        );

        metadata.put(
                "createTime",
                article.getCreateTime()
        );

        metadata.put(
                "updateTime",
                article.getUpdateTime()
        );

        metadata.put(
                "exportTime",
                LocalDateTime.now()
        );

        if (StringUtils.hasText(
                article.getModelConfig()
        )) {
            try {
                metadata.put(
                        "modelConfig",
                        objectMapper.readTree(
                                article.getModelConfig()
                        )
                );
            } catch (Exception e) {
                metadata.put(
                        "modelConfig",
                        article.getModelConfig()
                );
            }
        }

        return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(metadata);
    }

    private byte[] buildZip(
            String markdown,
            String html,
            byte[] metadata,
            List<ExportImage> images
    ) throws Exception {

        try (
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream();

                ZipOutputStream zip =
                        new ZipOutputStream(
                                output,
                                StandardCharsets.UTF_8
                        )
        ) {
            writeZipEntry(
                    zip,
                    "article.md",
                    markdown.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            writeZipEntry(
                    zip,
                    "article.html",
                    html.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            writeZipEntry(
                    zip,
                    "metadata.json",
                    metadata
            );

            for (ExportImage image : images) {
                writeZipEntry(
                        zip,
                        image.relativePath(),
                        image.binary()
                                .content()
                );
            }

            zip.finish();

            return output.toByteArray();
        }
    }

    private void writeZipEntry(
            ZipOutputStream zip,
            String entryName,
            byte[] content
    ) throws Exception {

        ZipEntry entry =
                new ZipEntry(entryName);

        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private String resolveTitle(
            Article article
    ) {
        if (StringUtils.hasText(
                article.getSelectedTitle()
        )) {
            return article
                    .getSelectedTitle()
                    .trim();
        }

        if (StringUtils.hasText(
                article.getTopic()
        )) {
            return article
                    .getTopic()
                    .trim();
        }

        return "未命名文章";
    }

    private String sanitizeFileName(
            String value
    ) {
        String result =
                value.replaceAll(
                                "[\\\\/:*?\"<>|]",
                                "_"
                        )
                        .replaceAll(
                                "\\s+",
                                "_"
                        )
                        .trim();

        if (result.length() > 60) {
            result =
                    result.substring(
                            0,
                            60
                    );
        }

        return StringUtils.hasText(result)
                ? result
                : "article";
    }

    private String resolveExtension(
            String value,
            String contentType
    ) {
        String lowerValue =
                value.toLowerCase(
                        Locale.ROOT
                );

        int queryIndex =
                lowerValue.indexOf('?');

        if (queryIndex >= 0) {
            lowerValue =
                    lowerValue.substring(
                            0,
                            queryIndex
                    );
        }

        if (lowerValue.endsWith(".png")) {
            return ".png";
        }

        if (lowerValue.endsWith(".webp")) {
            return ".webp";
        }

        if (lowerValue.endsWith(".gif")) {
            return ".gif";
        }

        if (lowerValue.endsWith(".jpeg")
                || lowerValue.endsWith(".jpg")) {
            return ".jpg";
        }

        return switch (
                contentType.toLowerCase(
                        Locale.ROOT
                )
                ) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private String contentTypeFromFileName(
            String fileName
    ) {
        String lower =
                fileName.toLowerCase(
                        Locale.ROOT
                );

        if (lower.contains(".png")) {
            return "image/png";
        }

        if (lower.contains(".webp")) {
            return "image/webp";
        }

        if (lower.contains(".gif")) {
            return "image/gif";
        }

        return "image/jpeg";
    }

    private String escapeHtml(
            String value
    ) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record ImageBinary(
            byte[] content,
            String contentType,
            String extension
    ) {
    }

    private record ExportImage(
            String originalUrl,
            String relativePath,
            ImageBinary binary
    ) {
    }
}
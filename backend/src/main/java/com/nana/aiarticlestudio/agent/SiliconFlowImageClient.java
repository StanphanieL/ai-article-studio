package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class SiliconFlowImageClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.siliconflow.api-key:}")
    private String apiKey;

    @Value("${app.siliconflow.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${app.siliconflow.image-model:Kwai-Kolors/Kolors}")
    private String imageModel;

    @Value("${app.siliconflow.image-size:1024x1024}")
    private String imageSize;

    @Value("${app.siliconflow.batch-size:1}")
    private Integer batchSize;

    @Value("${app.siliconflow.num-inference-steps:20}")
    private Integer numInferenceSteps;

    @Value("${app.siliconflow.guidance-scale:7.5}")
    private Double guidanceScale;

    @Value("${app.siliconflow.negative-prompt:}")
    private String negativePrompt;

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    @Value("${app.backend-base-url:http://localhost:8123}")
    private String backendBaseUrl;

    public SiliconFlowImageClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 使用 SiliconFlow 根据提示词生成图片。
     */
    public ImageResultOption generateImage(
            ImagePromptOption promptOption
    ) throws Exception {

        validateConfig();

        String prompt = buildPrompt(promptOption);
        String endpoint = removeTrailingSlash(baseUrl)
                + "/images/generations";

        Map<String, Object> requestBody = buildRequestBody(prompt);
        String requestJson = objectMapper.writeValueAsString(requestBody);

        System.out.println(
                "准备请求 SiliconFlow 生图接口：" + endpoint
        );
        System.out.println(
                "SiliconFlow image model = " + imageModel
        );
        System.out.println(
                "SiliconFlow prompt = " + prompt
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMinutes(3))
                .header(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                requestJson
                        )
                )
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            System.out.println(
                    "SiliconFlow 请求异常类型："
                            + e.getClass().getName()
            );
            System.out.println(
                    "SiliconFlow 请求异常信息："
                            + e.getMessage()
            );
            e.printStackTrace();
            throw e;
        }

        String traceId = response.headers()
                .firstValue("x-siliconcloud-trace-id")
                .orElse("");

        System.out.println(
                "SiliconFlow status = "
                        + response.statusCode()
        );
        System.out.println(
                "SiliconFlow traceId = "
                        + traceId
        );
        System.out.println(
                "SiliconFlow body = "
                        + response.body()
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new RuntimeException(
                    "SiliconFlow 生图请求失败，状态码："
                            + response.statusCode()
                            + "，traceId："
                            + traceId
                            + "，响应："
                            + response.body()
            );
        }

        String temporaryImageUrl =
                extractImageUrl(response.body());

        String localImageUrl =
                downloadAndSaveImage(temporaryImageUrl);

        ImageResultOption result =
                new ImageResultOption();

        result.setImageTitle(
                promptOption.getImageTitle()
        );

        result.setUsageScene(
                promptOption.getUsageScene()
        );

        result.setPromptZh(
                promptOption.getPromptZh()
        );

        result.setPromptEn(
                promptOption.getPromptEn()
        );

        // 前端使用本地永久地址，而不是 SiliconFlow 临时地址。
        result.setImageUrl(localImageUrl);

        result.setSource("SILICONFLOW");

        // SiliconFlow 临时图片地址会过期，不建议存为来源地址。
        result.setSourceUrl("");

        result.setAuthor(
                "SiliconFlow / " + imageModel
        );

        result.setAuthorUrl("");

        return result;
    }

    /**
     * 检查必要配置。
     */
    private void validateConfig() {
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException(
                    "未读取到 SILICONFLOW_API_KEY，"
                            + "请检查 IDEA Environment variables"
            );
        }

        if (!StringUtils.hasText(imageModel)) {
            throw new RuntimeException(
                    "SiliconFlow image-model 未配置"
            );
        }

        if (!StringUtils.hasText(imageSize)) {
            throw new RuntimeException(
                    "SiliconFlow image-size 未配置"
            );
        }
    }

    /**
     * 优先使用英文配图提示词。
     */
    private String buildPrompt(
            ImagePromptOption promptOption
    ) {
        if (promptOption == null) {
            throw new RuntimeException(
                    "ImagePromptOption 为空"
            );
        }

        if (StringUtils.hasText(
                promptOption.getPromptEn()
        )) {
            return promptOption.getPromptEn().trim();
        }

        if (StringUtils.hasText(
                promptOption.getPromptZh()
        )) {
            return promptOption.getPromptZh().trim();
        }

        if (StringUtils.hasText(
                promptOption.getImageTitle()
        )) {
            return promptOption.getImageTitle().trim();
        }

        throw new RuntimeException(
                "没有可以用于生图的提示词"
        );
    }

    /**
     * 按照 Kolors 官方示例构造请求参数。
     */
    private Map<String, Object> buildRequestBody(
            String prompt
    ) {
        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put("model", imageModel);
        body.put("prompt", prompt);
        body.put("image_size", imageSize);
        body.put("batch_size", batchSize);
        body.put(
                "num_inference_steps",
                numInferenceSteps
        );
        body.put(
                "guidance_scale",
                guidanceScale
        );

        if (StringUtils.hasText(negativePrompt)) {
            body.put(
                    "negative_prompt",
                    negativePrompt
            );
        }

        return body;
    }

    /**
     * 解析 SiliconFlow 返回的 images[0].url。
     */
    private String extractImageUrl(
            String responseBody
    ) throws Exception {

        JsonNode root =
                objectMapper.readTree(responseBody);

        JsonNode images =
                root.path("images");

        if (!images.isArray()
                || images.isEmpty()) {
            throw new RuntimeException(
                    "SiliconFlow 响应中没有 images 数组："
                            + responseBody
            );
        }

        String imageUrl =
                images.get(0)
                        .path("url")
                        .asText();

        if (!StringUtils.hasText(imageUrl)) {
            throw new RuntimeException(
                    "SiliconFlow 响应中没有 images[0].url："
                            + responseBody
            );
        }

        return imageUrl;
    }

    /**
     * 下载 SiliconFlow 临时图片并保存到本地。
     */
    private String downloadAndSaveImage(
            String temporaryImageUrl
    ) throws Exception {

        HttpRequest downloadRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(temporaryImageUrl))
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build();

        HttpResponse<byte[]> imageResponse =
                httpClient.send(
                        downloadRequest,
                        HttpResponse.BodyHandlers.ofByteArray()
                );

        if (imageResponse.statusCode() < 200
                || imageResponse.statusCode() >= 300) {

            throw new RuntimeException(
                    "下载 SiliconFlow 图片失败，状态码："
                            + imageResponse.statusCode()
            );
        }

        String contentType =
                imageResponse.headers()
                        .firstValue("Content-Type")
                        .orElse("image/png");

        String extension =
                resolveExtension(contentType);

        Path imageDirectory =
                Paths.get(imageLocalDir)
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(imageDirectory);

        String fileName =
                "siliconflow-"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        + extension;

        Path imagePath =
                imageDirectory.resolve(fileName);

        Files.write(
                imagePath,
                imageResponse.body(),
                StandardOpenOption.CREATE_NEW
        );

        String localImageUrl =
                removeTrailingSlash(backendBaseUrl)
                        + "/uploads/images/"
                        + fileName;

        System.out.println(
                "SiliconFlow 图片已保存到："
                        + imagePath
        );

        System.out.println(
                "SiliconFlow 图片访问地址："
                        + localImageUrl
        );

        return localImageUrl;
    }

    private String resolveExtension(
            String contentType
    ) {
        String normalized =
                contentType.toLowerCase(Locale.ROOT);

        if (normalized.contains("jpeg")
                || normalized.contains("jpg")) {
            return ".jpg";
        }

        if (normalized.contains("webp")) {
            return ".webp";
        }

        if (normalized.contains("gif")) {
            return ".gif";
        }

        return ".png";
    }

    private String removeTrailingSlash(
            String value
    ) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String result = value.trim();

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }
}
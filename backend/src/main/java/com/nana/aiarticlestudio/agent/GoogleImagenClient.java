package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import lombok.RequiredArgsConstructor;
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
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoogleImagenClient {

    private final ObjectMapper objectMapper;

    @Value("${app.google.api-key:}")
    private String apiKey;

    @Value("${app.google.image-model:imagen-4.0-generate-001}")
    private String imageModel;

    @Value("${app.image.local-dir:uploads/images}")
    private String imageLocalDir;

    @Value("${app.backend-base-url:http://localhost:8123}")
    private String backendBaseUrl;

    public ImageResultOption generateImage(ImagePromptOption promptOption) throws Exception {
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException("Google AI Studio API Key 未配置");
        }

        String prompt = buildPrompt(promptOption);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + imageModel
                + ":generateContent";

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of(
                                "parts", new Object[]{
                                        Map.of("text", prompt)
                                }
                        )
                }
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

//        HttpClient client = HttpClient.newHttpClient();
        HttpClient client = HttpClient.newBuilder()
                .proxy(java.net.ProxySelector.of(
                        new java.net.InetSocketAddress("127.0.0.1", 7890)
                ))
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

//        HttpResponse<String> response = client.send(
//                request,
//                HttpResponse.BodyHandlers.ofString()
//        );
//        System.out.println("Google Imagen status = " + response.statusCode());
//        System.out.println("Google Imagen body = " + response.body());
        HttpResponse<String> response;

        try {
            System.out.println("准备请求 Gemini 生图接口：" + url);

            response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Gemini image status = " + response.statusCode());
            System.out.println("Gemini image body = " + response.body());
        } catch (Exception e) {
            System.out.println("Gemini image request error class = " + e.getClass().getName());
            System.out.println("Gemini image request error message = " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Google Imagen 请求失败，状态码：" + response.statusCode()
                    + "，响应：" + response.body());
        }

        String base64 = extractBase64(response.body());

        String imageUrl = saveBase64Image(base64);

        ImageResultOption result = new ImageResultOption();
        result.setImageTitle(promptOption.getImageTitle());
        result.setUsageScene(promptOption.getUsageScene());
        result.setPromptZh(promptOption.getPromptZh());
        result.setPromptEn(promptOption.getPromptEn());
        result.setImageUrl(imageUrl);
        result.setSource("GOOGLE_AI");
        result.setSourceUrl("");
        result.setAuthor("Google Imagen");
        result.setAuthorUrl("");

        return result;
    }

    private String buildPrompt(ImagePromptOption promptOption) {
        if (StringUtils.hasText(promptOption.getPromptEn())) {
            return promptOption.getPromptEn();
        }

        if (StringUtils.hasText(promptOption.getPromptZh())) {
            return promptOption.getPromptZh();
        }

        if (StringUtils.hasText(promptOption.getImageTitle())) {
            return promptOption.getImageTitle();
        }

        return "A clean modern illustration for an article";
    }

    private String extractBase64(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode parts = root.at("/candidates/0/content/parts");

        if (parts.isArray()) {
            for (JsonNode part : parts) {
                JsonNode inlineData = part.path("inlineData");
                if (!inlineData.isMissingNode()) {
                    String data = inlineData.path("data").asText();
                    if (StringUtils.hasText(data)) {
                        return data;
                    }
                }

                JsonNode inlineDataSnake = part.path("inline_data");
                if (!inlineDataSnake.isMissingNode()) {
                    String data = inlineDataSnake.path("data").asText();
                    if (StringUtils.hasText(data)) {
                        return data;
                    }
                }
            }
        }

        throw new RuntimeException("Gemini 生图返回中未找到 inlineData.data：" + responseBody);
    }

    private String saveBase64Image(String base64) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64);

        Path dir = Paths.get(imageLocalDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String fileName = UUID.randomUUID().toString().replace("-", "") + ".png";
        Path imagePath = dir.resolve(fileName);

        Files.write(imagePath, imageBytes);

        return backendBaseUrl + "/uploads/images/" + fileName;
    }
}
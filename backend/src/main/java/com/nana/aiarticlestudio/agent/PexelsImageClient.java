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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PexelsImageClient {

    private final ObjectMapper objectMapper;

    @Value("${app.pexels.api-key:}")
    private String apiKey;

    public ImageResultOption searchPhoto(ImagePromptOption promptOption) throws Exception {
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException("Pexels API Key 未配置");
        }

        String query = buildQuery(promptOption);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        String url = "https://api.pexels.com/v1/search?query="
                + encodedQuery
                + "&per_page=1&orientation=landscape";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", apiKey)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Pexels 请求失败，状态码：" + response.statusCode()
                    + "，响应：" + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode photos = root.path("photos");

        if (!photos.isArray() || photos.isEmpty()) {
            throw new RuntimeException("Pexels 未找到匹配图片，query=" + query);
        }

        JsonNode photo = photos.get(0);

        ImageResultOption result = new ImageResultOption();
        result.setImageTitle(promptOption.getImageTitle());
        result.setUsageScene(promptOption.getUsageScene());
        result.setPromptZh(promptOption.getPromptZh());
        result.setPromptEn(promptOption.getPromptEn());

        result.setImageUrl(photo.path("src").path("large").asText());
        result.setSource("PEXELS");
        result.setSourceUrl(photo.path("url").asText());
        result.setAuthor(photo.path("photographer").asText());
        result.setAuthorUrl(photo.path("photographer_url").asText());

        return result;
    }

    private String buildQuery(ImagePromptOption promptOption) {
        if (StringUtils.hasText(promptOption.getImageTitle())) {
            return promptOption.getImageTitle();
        }

        if (StringUtils.hasText(promptOption.getUsageScene())) {
            return promptOption.getUsageScene();
        }

        if (StringUtils.hasText(promptOption.getPromptEn())) {
            String prompt = promptOption.getPromptEn()
                    .replaceAll("[^a-zA-Z0-9\\s]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            if (prompt.length() > 80) {
                return prompt.substring(0, 80);
            }

            return prompt;
        }

        return "technology writing";
    }
}
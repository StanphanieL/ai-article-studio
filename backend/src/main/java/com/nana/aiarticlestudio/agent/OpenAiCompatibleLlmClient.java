package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-seconds:60}")
    private long timeoutSeconds;

    @Override
    public String chat(String prompt) {
        try {
            if (!StringUtils.hasText(apiKey)) {
                throw new RuntimeException("LLM_API_KEY 未配置");
            }

            String url = baseUrl + "/v1/chat/completions";

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", 0.7
            );

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("大模型调用失败，状态码：" + response.statusCode()
                        + "，响应：" + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());

            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode() || !StringUtils.hasText(contentNode.asText())) {
                throw new RuntimeException("大模型响应中没有 content：" + response.body());
            }

            return contentNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("调用大模型失败：" + e.getMessage(), e);
        }
    }
}
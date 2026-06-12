package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class OpenAiCompatibleStreamingLlmClient implements StreamingLlmClient {

    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-seconds:120}")
    private long timeoutSeconds;

    @Override
    public String chatStream(String prompt, StreamChunkHandler handler) {
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
                    "temperature", 0.7,
                    "stream", true
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

            HttpResponse<java.io.InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("大模型流式调用失败，状态码：" + response.statusCode()
                        + "，响应：" + errorBody);
            }

            StringBuilder fullContent = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8)
            )) {
                String line;

                while ((line = reader.readLine()) != null) {
                    if (!StringUtils.hasText(line)) {
                        continue;
                    }

                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data = line.substring("data:".length()).trim();

                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    String chunk = parseChunk(data);

                    if (StringUtils.hasText(chunk)) {
                        fullContent.append(chunk);
                        handler.onChunk(chunk);
                    }
                }
            }

            return fullContent.toString();
        } catch (Exception e) {
            throw new RuntimeException("流式调用大模型失败：" + e.getMessage(), e);
        }
    }

    private String parseChunk(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }

            JsonNode firstChoice = choices.get(0);

            JsonNode deltaContent = firstChoice
                    .path("delta")
                    .path("content");

            if (deltaContent.isTextual()) {
                return deltaContent.asText();
            }

            JsonNode messageContent = firstChoice
                    .path("message")
                    .path("content");

            if (messageContent.isTextual()) {
                return messageContent.asText();
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
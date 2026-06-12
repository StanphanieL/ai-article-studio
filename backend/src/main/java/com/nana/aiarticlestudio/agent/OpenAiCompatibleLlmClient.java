package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "llm.mode",
        havingValue = "real"
)
public class OpenAiCompatibleLlmClient
        implements LlmClient {

    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${llm.max-completion-tokens:8192}")
    private Integer defaultMaxCompletionTokens;

    @Value("${llm.timeout-seconds:60}")
    private long timeoutSeconds;

    @Override
    public String chat(String prompt) {
        return chat(prompt, null);
    }

    @Override
    public String chat(
            String prompt,
            LlmRequestOptions options
    ) {
        try {
            if (!StringUtils.hasText(apiKey)) {
                throw new RuntimeException(
                        "LLM_API_KEY 未配置"
                );
            }

            String requestModel =
                    options != null
                            && StringUtils.hasText(
                            options.getModel()
                    )
                            ? options.getModel()
                            : model;

            Double requestTemperature =
                    options != null
                            && options.getTemperature()
                            != null
                            ? options.getTemperature()
                            : defaultTemperature;

            Integer requestMaxTokens =
                    options != null
                            && options
                            .getMaxCompletionTokens()
                            != null
                            ? options
                            .getMaxCompletionTokens()
                            : defaultMaxCompletionTokens;

            String url =
                    baseUrl
                            + "/v1/chat/completions";

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "model",
                    requestModel
            );

            body.put(
                    "messages",
                    List.of(
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    prompt
                            )
                    )
            );

            body.put(
                    "temperature",
                    requestTemperature
            );

            body.put(
                    "max_completion_tokens",
                    requestMaxTokens
            );

            String requestBody =
                    objectMapper.writeValueAsString(
                            body
                    );

            System.out.println(
                    "LLM model = "
                            + requestModel
            );

            System.out.println(
                    "LLM temperature = "
                            + requestTemperature
            );

            System.out.println(
                    "LLM maxCompletionTokens = "
                            + requestMaxTokens
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(
                                    Duration.ofSeconds(
                                            timeoutSeconds
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + apiKey
                            )
                            .POST(
                                    HttpRequest
                                            .BodyPublishers
                                            .ofString(
                                                    requestBody
                                            )
                            )
                            .build();

            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(
                                    Duration.ofSeconds(
                                            timeoutSeconds
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse
                                    .BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "大模型调用失败，状态码："
                                + response.statusCode()
                                + "，响应："
                                + response.body()
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            JsonNode contentNode =
                    root.path("choices")
                            .path(0)
                            .path("message")
                            .path("content");

            if (contentNode.isMissingNode()
                    || !StringUtils.hasText(
                    contentNode.asText()
            )) {
                throw new RuntimeException(
                        "大模型响应中没有 content："
                                + response.body()
                );
            }

            return contentNode.asText();

        } catch (Exception e) {
            throw new RuntimeException(
                    "调用大模型失败："
                            + e.getMessage(),
                    e
            );
        }
    }
}
package com.nana.aiarticlestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.dto.ArticleModelConfig;
import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleModelConfigService {

    private static final Set<String> IMAGE_PROVIDERS =
            Set.of(
                    "AUTO",
                    "PEXELS",
                    "SILICONFLOW",
                    "GOOGLE_AI"
            );

    private static final Set<String> IMAGE_COUNT_MODES =
            Set.of(
                    "AUTO",
                    "CUSTOM"
            );

    private final ObjectMapper objectMapper;

    @Value("${llm.model:MiniMax-M2.5}")
    private String defaultTextModel;

    @Value("${llm.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${llm.max-completion-tokens:8192}")
    private Integer defaultMaxCompletionTokens;

    @Value("${app.model-config.image-provider:AUTO}")
    private String defaultImageProvider;

    @Value("${app.model-config.image-count-mode:AUTO}")
    private String defaultImageCountMode;

    @Value("${app.model-config.image-count:5}")
    private Integer defaultImageCount;

    public ArticleModelConfig getDefaultConfig() {
        return normalize(
                new ArticleModelConfig()
        );
    }

    public ArticleModelConfig resolve(
            String modelConfigJson
    ) {
        if (!StringUtils.hasText(
                modelConfigJson
        )) {
            return getDefaultConfig();
        }

        try {
            ArticleModelConfig config =
                    objectMapper.readValue(
                            modelConfigJson,
                            ArticleModelConfig.class
                    );

            return normalize(config);
        } catch (Exception e) {
            throw new RuntimeException(
                    "解析模型配置失败："
                            + e.getMessage(),
                    e
            );
        }
    }

    public ArticleModelConfig normalize(
            ArticleModelConfig config
    ) {
        if (config == null) {
            config = new ArticleModelConfig();
        }

        if (!StringUtils.hasText(
                config.getTextModel()
        )) {
            config.setTextModel(
                    defaultTextModel
            );
        } else {
            config.setTextModel(
                    config.getTextModel().trim()
            );
        }

        if (config.getTemperature() == null) {
            config.setTemperature(
                    defaultTemperature
            );
        }

        if (config.getMaxCompletionTokens()
                == null) {
            config.setMaxCompletionTokens(
                    defaultMaxCompletionTokens
            );
        }

        if (!StringUtils.hasText(
                config.getImageProvider()
        )) {
            config.setImageProvider(
                    defaultImageProvider
            );
        }

        config.setImageProvider(
                config.getImageProvider()
                        .trim()
                        .toUpperCase()
        );

        if (!StringUtils.hasText(
                config.getImageCountMode()
        )) {
            config.setImageCountMode(
                    defaultImageCountMode
            );
        }

        config.setImageCountMode(
                config.getImageCountMode()
                        .trim()
                        .toUpperCase()
        );

        if (config.getImageCount() == null) {
            config.setImageCount(
                    defaultImageCount
            );
        }

        validate(config);

        return config;
    }

    public String toJson(
            ArticleModelConfig config
    ) {
        try {
            return objectMapper.writeValueAsString(
                    normalize(config)
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "序列化模型配置失败："
                            + e.getMessage(),
                    e
            );
        }
    }

    public LlmRequestOptions toLlmOptions(
            ArticleModelConfig config
    ) {
        ArticleModelConfig normalized =
                normalize(config);

        return new LlmRequestOptions(
                normalized.getTextModel(),
                normalized.getTemperature(),
                normalized.getMaxCompletionTokens()
        );
    }

    private void validate(
            ArticleModelConfig config
    ) {
        if (config.getTemperature() < 0
                || config.getTemperature() > 2) {
            throw new RuntimeException(
                    "temperature 必须在 0～2 之间"
            );
        }

        if (config.getMaxCompletionTokens() < 1
                || config.getMaxCompletionTokens()
                > 65536) {
            throw new RuntimeException(
                    "maxCompletionTokens 必须在 1～65536 之间"
            );
        }

        if (!IMAGE_PROVIDERS.contains(
                config.getImageProvider()
        )) {
            throw new RuntimeException(
                    "不支持的图片来源："
                            + config.getImageProvider()
            );
        }

        if (!IMAGE_COUNT_MODES.contains(
                config.getImageCountMode()
        )) {
            throw new RuntimeException(
                    "imageCountMode 只能是 AUTO 或 CUSTOM"
            );
        }

        if ("CUSTOM".equals(
                config.getImageCountMode()
        )) {
            if (config.getImageCount() < 3
                    || config.getImageCount() > 8) {
                throw new RuntimeException(
                        "自定义配图数量必须在 3～8 之间"
                );
            }
        }
    }
}
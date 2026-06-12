package com.nana.aiarticlestudio.model.dto;

import lombok.Data;

@Data
public class ArticleModelConfig {

    private String textModel;

    private Double temperature;

    private Integer maxCompletionTokens;

    private String imageProvider;

    private String imageCountMode;

    private Integer imageCount;
}
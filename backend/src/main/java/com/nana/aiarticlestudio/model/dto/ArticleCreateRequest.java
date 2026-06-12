package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArticleCreateRequest {

    @NotBlank(message = "选题不能为空")
    private String topic;

    private String style;
}
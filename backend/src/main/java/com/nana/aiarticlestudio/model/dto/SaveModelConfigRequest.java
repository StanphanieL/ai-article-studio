package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveModelConfigRequest {

    @NotBlank(message = "taskId 不能为空")
    private String taskId;

    @NotBlank(message = "文章风格不能为空")
    private String style;

    @Valid
    @NotNull(message = "模型配置不能为空")
    private ArticleModelConfig config;
}
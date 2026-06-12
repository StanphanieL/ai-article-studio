package com.nana.aiarticlestudio.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveTitleRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotBlank(message = "标题不能为空")
    private String selectedTitle;
}
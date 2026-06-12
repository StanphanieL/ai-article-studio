package com.nana.aiarticlestudio.model.dto;

import com.nana.aiarticlestudio.model.vo.OutlineItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaveOutlineRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotEmpty(message = "大纲不能为空")
    private List<OutlineItem> outline;
}
package com.nana.aiarticlestudio.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentLog {

    private Long id;

    private String taskId;

    private String agentName;

    private String inputText;

    private String outputText;

    private String status;

    private Long costMs;

    private String errorMessage;

    private LocalDateTime createTime;
}